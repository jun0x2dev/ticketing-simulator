package com.ticketing.api.domain.seat.service.impl;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.outbox.service.OutboxService;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.domain.seat.dto.SeatResponse;
import com.ticketing.api.domain.seat.dto.SeatStatusChangedEvent;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.lock.SeatLockManager;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * SeatService 구현체. 좌석 선점은 Redis 분산 락으로 동시성을 제어한 뒤,
 * 락을 통과한 단 하나의 요청만 DB 상태를 변경하도록 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatServiceImpl implements SeatService {

    private static final Logger log = LoggerFactory.getLogger(SeatServiceImpl.class);

    private final SeatRepository seatRepository;
    private final SeatLockManager seatLockManager;
    private final EntryTokenManager entryTokenManager;
    private final ApplicationEventPublisher eventPublisher;
    private final OutboxService outboxService;

    @Override
    @Transactional
    public void initializeSeats(Event event, int seatCount) {
        List<Seat> seats = IntStream.rangeClosed(1, seatCount)
                .mapToObj(seatNumber -> Seat.create(event, seatNumber))
                .toList();
        seatRepository.saveAll(seats);
        log.info("좌석 초기화 완료 - eventId: {}, seatCount: {}", event.getId(), seatCount);
    }

    @Override
    public List<SeatResponse> getSeats(Long eventId) {
        return seatRepository.findByEventIdOrderBySeatNumberAsc(eventId).stream()
                .map(SeatResponse::from)
                .toList();
    }

    @Override
    public SeatResponse getSeat(Long seatId) {
        return seatRepository.findById(seatId)
                .map(SeatResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.SEAT_NOT_FOUND));
    }

    @Override
    public SeatResponse getHeldSeat(Long seatId, String entryToken) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new CustomException(ErrorCode.SEAT_NOT_FOUND));
        seat.verifyHolder(entryToken);
        return SeatResponse.from(seat);
    }

    @Override
    @Transactional
    public void confirmSeat(Long seatId, String entryToken) {
        // 행을 잠근 뒤 선점자를 확인해야, 결제 중 TTL 복구나 다른 결제 요청이 먼저 상태를 바꾼 경우를 놓치지 않는다.
        Seat seat = findSeatForUpdate(seatId);
        seat.verifyHolder(entryToken);
        seat.confirm();
        publishStatusChanged(seat);
        // 확정된 좌석은 더 이상 이 토큰의 선점이 아니므로, 같은 토큰으로 다음 좌석을 잡을 수 있게 표시를 지운다.
        // 커밋 전에 지우면 커밋이 실패했을 때 HELD 좌석을 가진 채 다른 좌석을 또 잡을 수 있어 커밋 뒤에 지운다.
        afterCommit(() -> seatLockManager.clearHolder(entryToken));
        log.info("좌석 확정 완료 - seatId: {}", seatId);
    }

    @Override
    @Transactional
    public SeatResponse holdSeat(Long seatId, String entryToken) {
        // 0. 토큰이 없거나 만료된 요청은 락/DB를 건드리기 전에 Redis 조회 한 번으로 걸러낸다.
        Long tokenEventId = entryTokenManager.findEventId(entryToken)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTRY_TOKEN_INVALID));

        // 1. 토큰 단위 표시를 좌석 락보다 먼저 건다. 대기열을 통과한 한 사람이 여러 좌석을 쓸어 담지 못하게 한다.
        if (!seatLockManager.tryMarkHolder(entryToken, seatId, Seat.HOLD_TTL)) {
            throw new CustomException(ErrorCode.SEAT_HOLD_LIMIT_EXCEEDED);
        }

        // 2. Redis 락 선점 시도 — 동시에 여러 요청이 들어와도 SET NX 1회 명령으로 단 하나만 성공한다.
        String lockToken = seatLockManager.tryLock(seatId, Seat.HOLD_TTL);
        if (lockToken == null) {
            seatLockManager.clearHolder(entryToken);
            throw new CustomException(ErrorCode.SEAT_ALREADY_HELD);
        }

        // DB 반영이 커밋되지 않으면(아래 검증 예외든 커밋 단계의 실패든) 락과 토큰 표시를 바로 풀어,
        // 좌석과 사용자가 5분 동안 묶이지 않고 다시 시도할 수 있게 한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    seatLockManager.unlock(seatId, lockToken);
                    seatLockManager.clearHolder(entryToken);
                }
            }
        });

        // 3. 락을 통과한 요청만 DB 상태를 HELD로 변경한다.
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new CustomException(ErrorCode.SEAT_NOT_FOUND));
        // 다른 이벤트의 입장 토큰으로 이 이벤트의 좌석을 잡지 못하게 한다.
        if (!seat.getEvent().getId().equals(tokenEventId)) {
            throw new CustomException(ErrorCode.ENTRY_TOKEN_INVALID);
        }
        seat.hold(entryToken);
        publishStatusChanged(seat);
        log.info("좌석 선점 완료 - seatId: {}", seatId);
        return SeatResponse.from(seat);
    }

    @Override
    @Transactional
    public void releaseSeat(Long seatId, String entryToken) {
        Seat seat = findSeatForUpdate(seatId);
        seat.verifyHolder(entryToken);
        seat.release();
        publishStatusChanged(seat);
        // 커밋 전에 락을 풀면, 커밋이 실패해 HELD로 남은 좌석을 다른 요청이 락만 잡고 들어올 수 있어 커밋 뒤에 푼다.
        afterCommit(() -> {
            seatLockManager.forceUnlock(seatId);
            seatLockManager.clearHolder(entryToken);
        });
        log.info("좌석 선점 해제 완료 - seatId: {}", seatId);
    }

    @Override
    @Transactional
    public List<SeatResponse> releaseExpiredSeats(LocalDateTime heldBefore) {
        List<SeatResponse> released = seatRepository.findExpiredHeldSeatsForUpdate(heldBefore).stream()
                .map(seat -> {
                    seat.release();
                    publishStatusChanged(seat);
                    // 복구와 같은 트랜잭션에 seat.released를 기록해, 복구가 커밋되면 이벤트도 반드시 발행되게 한다.
                    Long eventId = seat.getEvent().getId();
                    outboxService.append(KafkaTopicConfig.SEAT_RELEASED, String.valueOf(eventId),
                            SeatReleasedEvent.ttlExpired(eventId, seat.getId()));
                    return SeatResponse.from(seat);
                })
                .toList();
        if (!released.isEmpty()) {
            log.info("TTL 만료 좌석 복구 완료 - count: {}", released.size());
        }
        return released;
    }

    @Override
    @Transactional
    public int deleteSeatsOfEvents(List<Long> eventIds) {
        return seatRepository.deleteByEventIds(eventIds);
    }

    // Redis 정리는 DB 변경이 실제로 커밋된 뒤에만 해야 두 저장소의 상태가 어긋나지 않는다.
    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    // 결제 확정/해제처럼 TTL 복구와 경쟁하는 변경은 행 잠금으로 읽어 순서를 보장한다.
    private Seat findSeatForUpdate(Long seatId) {
        return seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new CustomException(ErrorCode.SEAT_NOT_FOUND));
    }

    // 커밋 이후 WebSocket 브로드캐스트를 위해 상태 변경을 애플리케이션 이벤트로 알린다.
    private void publishStatusChanged(Seat seat) {
        eventPublisher.publishEvent(new SeatStatusChangedEvent(seat.getEvent().getId(), seat.getId(), seat.getStatus()));
    }
}
