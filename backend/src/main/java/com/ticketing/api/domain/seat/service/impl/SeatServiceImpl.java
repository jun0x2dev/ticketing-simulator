package com.ticketing.api.domain.seat.service.impl;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.seat.dto.SeatResponse;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.lock.SeatLockManager;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SeatService 구현체. 좌석 선점은 Redis 분산 락으로 동시성을 제어한 뒤,
 * 락을 통과한 단 하나의 요청만 DB 상태를 변경하도록 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatServiceImpl implements SeatService {

    private static final Logger log = LoggerFactory.getLogger(SeatServiceImpl.class);

    // 결제 제한시간과 동일한 값. 이 시간 내 결제가 완료되지 않으면 락이 자동 만료되어 좌석이 재선점 가능해진다.
    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final SeatRepository seatRepository;
    private final SeatLockManager seatLockManager;

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
    @Transactional
    public SeatResponse holdSeat(Long seatId) {
        // 1. Redis 락 선점 시도 — 동시에 여러 요청이 들어와도 SET NX 1회 명령으로 단 하나만 성공한다.
        String lockToken = seatLockManager.tryLock(seatId, HOLD_TTL);
        if (lockToken == null) {
            throw new CustomException(ErrorCode.SEAT_ALREADY_HELD);
        }

        try {
            // 2. 락을 통과한 요청만 DB 상태를 HELD로 변경한다.
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new CustomException(ErrorCode.SEAT_NOT_FOUND));
            seat.hold();
            log.info("좌석 선점 완료 - seatId: {}", seatId);
            return SeatResponse.from(seat);
        } catch (RuntimeException e) {
            // DB 반영이 실패하면 락을 바로 해제해서 다른 요청이 재시도할 수 있게 한다.
            seatLockManager.unlock(seatId, lockToken);
            throw e;
        }
    }

    @Override
    @Transactional
    public void releaseSeat(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new CustomException(ErrorCode.SEAT_NOT_FOUND));
        seat.release();
        seatLockManager.forceUnlock(seatId);
        log.info("좌석 선점 해제 완료 - seatId: {}", seatId);
    }
}
