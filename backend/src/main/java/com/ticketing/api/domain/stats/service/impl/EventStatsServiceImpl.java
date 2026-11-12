package com.ticketing.api.domain.stats.service.impl;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.stats.dto.EventStatsResponse;
import com.ticketing.api.domain.stats.recorder.EventStatsRecorder;
import com.ticketing.api.domain.stats.service.EventStatsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * EventStatsService 구현체. DB를 쓰지 않고 Redis 카운터에만 기록한다.
 * DB를 쓰지 않으므로 트랜잭션을 걸지 않는다. 걸면 통계 조회와 Consumer 집계마다 DB 커넥션을 잡아,
 * 부하 중 좌석 선점·결제와 커넥션을 다투게 된다.
 */
@Service
@RequiredArgsConstructor
public class EventStatsServiceImpl implements EventStatsService {

    private static final Logger log = LoggerFactory.getLogger(EventStatsServiceImpl.class);

    private final EventStatsRecorder recorder;

    @Override
    public void recordGranted(EntryGrantedEvent event) {
        record(event.eventId(), EventStatsResponse.GRANTED, "granted:" + event.requestId());
    }

    @Override
    public void recordConfirmed(SeatConfirmedEvent event) {
        record(event.eventId(), EventStatsResponse.CONFIRMED, "confirmed:" + event.paymentId());
    }

    @Override
    public void recordReleased(SeatReleasedEvent event) {
        // 결제 실패와 예매 취소는 paymentId로 식별하고, 결제 기록이 없는 시간 초과 해제는 좌석과 발생 시각으로 식별한다.
        // 예매 취소의 paymentId는 seat.confirmed와 같은 결제의 것이라 접두어로 구분한다.
        switch (event.reason()) {
            case SeatReleasedEvent.REASON_TTL_EXPIRED -> record(event.eventId(), EventStatsResponse.TTL_EXPIRED,
                    "released:ttl:" + event.seatId() + ":" + event.occurredAt().toEpochMilli());
            case SeatReleasedEvent.REASON_BOOKING_CANCELLED -> record(event.eventId(), EventStatsResponse.CANCELLED,
                    "released:cancel:" + event.paymentId());
            default -> record(event.eventId(), EventStatsResponse.PAYMENT_FAILED,
                    "released:payment:" + event.paymentId());
        }
    }

    @Override
    public EventStatsResponse getStats(Long eventId) {
        return EventStatsResponse.of(eventId, recorder.getAll(eventId));
    }

    private void record(Long eventId, String field, String dedupKey) {
        if (!recorder.incrementOnce(eventId, field, dedupKey)) {
            log.info("이미 집계된 메시지 - eventId: {}, key: {}", eventId, dedupKey);
        }
    }
}
