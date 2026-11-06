package com.ticketing.api.domain.payment.entity;

import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 좌석 1석에 대한 Mock 결제 시도 결과를 기록하는 엔티티.
 * seat 도메인과 결합하지 않도록 Seat 엔티티 대신 seatId만 보관한다.
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    // 게이트웨이 승인 번호. 승인된 결제(SUCCESS, CANCELLED, REFUNDED)만 값이 있고, 취소/대사(정산 대조)에 쓴다.
    @Column(name = "approval_id", length = 64)
    private String approvalId;

    // 결제한 사용자의 입장 토큰. 확정되면 좌석의 선점자 정보가 지워지므로, 예매 취소 요청이 본인 것인지는 이 값으로 확인한다.
    @Column(name = "payer_token", length = 36)
    private String payerToken;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private Payment(Long seatId, PaymentStatus status, String approvalId, String payerToken) {
        this.seatId = seatId;
        this.status = status;
        this.approvalId = approvalId;
        this.payerToken = payerToken;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 결제 결과를 기록하는 정적 팩토리 메서드.
     *
     * @param seatId     결제 대상 좌석 ID
     * @param status     결제 결과
     * @param approvalId 게이트웨이 승인 번호 (거절이면 null)
     * @param payerToken 결제한 사용자의 입장 토큰
     * @return 생성된 Payment
     */
    public static Payment create(Long seatId, PaymentStatus status, String approvalId, String payerToken) {
        return new Payment(seatId, status, approvalId, payerToken);
    }

    /**
     * 주어진 입장 토큰으로 취소할 수 있는 결제인지 확인한다. 게이트웨이에 환불을 요청하기 전에 호출한다.
     *
     * @param payerToken 취소를 요청한 사용자의 입장 토큰
     */
    public void verifyCancellable(String payerToken) {
        if (payerToken == null || !payerToken.equals(this.payerToken)) {
            throw new CustomException(ErrorCode.PAYMENT_PAYER_MISMATCH);
        }
        if (this.status != PaymentStatus.SUCCESS) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_CANCELLABLE);
        }
    }

    /**
     * 예매 취소로 환불 처리한다. 승인된 결제(SUCCESS)에서만 가능하다.
     *
     * @param payerToken 취소를 요청한 사용자의 입장 토큰
     */
    public void refund(String payerToken) {
        verifyCancellable(payerToken);
        this.status = PaymentStatus.REFUNDED;
    }
}
