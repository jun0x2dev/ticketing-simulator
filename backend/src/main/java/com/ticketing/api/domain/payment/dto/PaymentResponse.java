package com.ticketing.api.domain.payment.dto;

import com.ticketing.api.domain.payment.entity.Payment;
import com.ticketing.api.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;

/**
 * 결제 결과 응답 DTO. 결제 거절(FAILED)도 정상적인 처리 결과로 응답한다.
 */
public record PaymentResponse(
        Long id,
        Long seatId,
        PaymentStatus status,
        LocalDateTime createdAt
) {

    /**
     * Payment 엔티티를 PaymentResponse로 변환한다.
     *
     * @param payment 변환할 엔티티
     * @return 변환된 응답 DTO
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getSeatId(), payment.getStatus(), payment.getCreatedAt());
    }
}
