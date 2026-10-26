package com.ticketing.api.domain.payment.dto;

import java.time.Instant;

/**
 * "seat.released" 토픽으로 발행되는 좌석 해제 이벤트.
 * paymentId는 결제 실패로 해제된 경우에만 값이 있고, Consumer는 이를 멱등성 키로 사용한다.
 */
public record SeatReleasedEvent(
        Long eventId,
        Long seatId,
        Long paymentId,
        String reason,
        Instant occurredAt
) {

    public static final String REASON_PAYMENT_FAILED = "PAYMENT_FAILED";

    /**
     * 결제 실패로 인한 좌석 해제 이벤트를 생성한다.
     *
     * @param eventId   좌석이 속한 이벤트 ID
     * @param seatId    해제된 좌석 ID
     * @param paymentId 실패한 결제 ID
     * @return 생성된 이벤트
     */
    public static SeatReleasedEvent paymentFailed(Long eventId, Long seatId, Long paymentId) {
        return new SeatReleasedEvent(eventId, seatId, paymentId, REASON_PAYMENT_FAILED, Instant.now());
    }
}
