package com.ticketing.api.domain.payment.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 결제 요청 DTO. 선점(HELD)된 좌석 ID로 결제 대상을 지정한다.
 */
public record PaymentRequest(
        @NotNull Long seatId
) {
}
