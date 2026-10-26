package com.ticketing.api.domain.payment.entity;

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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private Payment(Long seatId, PaymentStatus status) {
        this.seatId = seatId;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 결제 결과를 기록하는 정적 팩토리 메서드.
     *
     * @param seatId 결제 대상 좌석 ID
     * @param status 결제 결과
     * @return 생성된 Payment
     */
    public static Payment create(Long seatId, PaymentStatus status) {
        return new Payment(seatId, status);
    }
}
