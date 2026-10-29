package com.ticketing.api.domain.stats.dto;

import java.util.Map;

/**
 * 회차별 예매 통계 응답 DTO. Kafka 이벤트(entry.granted, seat.confirmed, seat.released)를 집계한 값이다.
 */
public record EventStatsResponse(
        Long eventId,
        long granted,
        long confirmed,
        long paymentFailed,
        long ttlExpired
) {

    public static final String GRANTED = "granted";
    public static final String CONFIRMED = "confirmed";
    public static final String PAYMENT_FAILED = "paymentFailed";
    public static final String TTL_EXPIRED = "ttlExpired";

    /**
     * Redis에 기록된 항목 맵을 응답으로 변환한다. 아직 한 번도 기록되지 않은 항목은 0이다.
     *
     * @param eventId 회차(이벤트) ID
     * @param counts  항목 이름 → 값
     * @return 통계 응답
     */
    public static EventStatsResponse of(Long eventId, Map<String, Long> counts) {
        return new EventStatsResponse(eventId,
                counts.getOrDefault(GRANTED, 0L),
                counts.getOrDefault(CONFIRMED, 0L),
                counts.getOrDefault(PAYMENT_FAILED, 0L),
                counts.getOrDefault(TTL_EXPIRED, 0L));
    }
}
