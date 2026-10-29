package com.ticketing.api.domain.payment.gateway;

/**
 * 결제 게이트웨이의 승인 결과. 승인된 결제를 나중에 취소하려면 승인 번호가 필요하다.
 *
 * @param approved   승인 여부
 * @param approvalId 승인 번호 (거절이면 null)
 */
public record ChargeResult(boolean approved, String approvalId) {

    /**
     * 승인 결과를 만든다.
     *
     * @param approvalId 게이트웨이가 발급한 승인 번호
     * @return 승인된 결과
     */
    public static ChargeResult approved(String approvalId) {
        return new ChargeResult(true, approvalId);
    }

    /**
     * 거절 결과를 만든다.
     *
     * @return 거절된 결과
     */
    public static ChargeResult declined() {
        return new ChargeResult(false, null);
    }
}
