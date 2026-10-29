package com.ticketing.api.domain.payment.service;

import com.ticketing.api.domain.payment.dto.PaymentResponse;

/**
 * Mock 결제 처리 비즈니스 로직 인터페이스.
 */
public interface PaymentService {

    /**
     * 선점(HELD)된 좌석을 결제한다. 승인되면 좌석을 확정하고 seat.confirmed를,
     * 거절되면 좌석을 해제하고 seat.released를 발행한다.
     *
     * 좌석을 선점한 사용자만 결제할 수 있다.
     *
     * @param seatId     결제할 좌석 ID
     * @param entryToken 좌석을 선점할 때 쓴 입장 토큰
     * @return 결제 결과 (거절도 FAILED 상태로 반환)
     */
    PaymentResponse pay(Long seatId, String entryToken);
}
