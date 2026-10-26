package com.ticketing.api.domain.payment.dto;

import java.time.Instant;

/**
 * "seat.confirmed" 토픽으로 발행되는 결제 완료 이벤트.
 * Consumer는 paymentId를 멱등성 키로 사용해 중복 소비를 막는다.
 */
public record SeatConfirmedEvent(
        Long eventId,
        Long seatId,
        Long paymentId,
        Instant occurredAt
) {

    /**
     * 결제 완료 이벤트를 생성한다.
     *
     * @param eventId   좌석이 속한 이벤트 ID
     * @param seatId    확정된 좌석 ID
     * @param paymentId 결제 ID
     * @return 생성된 이벤트
     */
    public static SeatConfirmedEvent of(Long eventId, Long seatId, Long paymentId) {
        return new SeatConfirmedEvent(eventId, seatId, paymentId, Instant.now());
    }
}
