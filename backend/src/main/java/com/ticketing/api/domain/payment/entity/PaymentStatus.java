package com.ticketing.api.domain.payment.entity;

/**
 * 결제 처리 결과 상태.
 */
public enum PaymentStatus {
    SUCCESS,
    FAILED,
    // 승인됐지만 좌석 확정에 실패해 게이트웨이에 승인 취소를 요청한 결제
    CANCELLED
}
