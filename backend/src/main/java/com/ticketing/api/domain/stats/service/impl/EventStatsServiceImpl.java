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
import org.springframework.transaction.annotation.Transactional;

/**
 * EventStatsService 구현체. DB를 쓰지 않고 Redis 카운터에만 기록한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
        boolean ttlExpired = SeatReleasedEvent.REASON_TTL_EXPIRED.equals(event.reason());
        // 결제 실패는 paymentId로 식별하고, 결제 기록이 없는 시간 초과 해제는 좌석과 발생 시각으로 식별한다.
        String dedupKey = ttlExpired
                ? "released:ttl:" + event.seatId() + ":" + event.occurredAt().toEpochMilli()
                : "released:payment:" + event.paymentId();
        record(event.eventId(), ttlExpired ? EventStatsResponse.TTL_EXPIRED : EventStatsResponse.PAYMENT_FAILED, dedupKey);
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
