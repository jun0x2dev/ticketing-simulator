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
     * 좌석을 선점한 사용자만 결제할 수 있고, 같은 좌석의 결제가 진행 중이면 PAYMENT_IN_PROGRESS로 거절한다.
     * 승인 후 좌석 확정에 실패하면 승인을 취소하고(CANCELLED 기록) 확정 실패 예외를 그대로 던진다.
     *
     * @param seatId     결제할 좌석 ID
     * @param entryToken 좌석을 선점할 때 쓴 입장 토큰
     * @return 결제 결과 (거절도 FAILED 상태로 반환)
     */
    PaymentResponse pay(Long seatId, String entryToken);

    /**
     * 예매를 취소한다. 게이트웨이에 환불을 요청한 뒤, 결제를 REFUNDED로 바꾸고 좌석을 다시 열며 seat.released를 발행한다.
     *
     * 결제한 사용자만 취소할 수 있고(PAYMENT_PAYER_MISMATCH), 승인된 결제(SUCCESS)만 취소할 수 있다(PAYMENT_NOT_CANCELLABLE).
     * 같은 좌석의 결제나 취소가 진행 중이면 PAYMENT_IN_PROGRESS로 거절해 환불이 두 번 나가지 않게 한다.
     *
     * @param paymentId  취소할 결제 ID
     * @param entryToken 결제할 때 쓴 입장 토큰
     * @return 취소된 결제 (REFUNDED)
     */
    PaymentResponse cancel(Long paymentId, String entryToken);
}
