package com.ticketing.api.domain.payment.repository;

import com.ticketing.api.domain.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Payment 엔티티에 대한 기본 CRUD를 제공하는 레포지토리.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 결제 1건을 행 잠금(FOR UPDATE)으로 조회한다. 같은 결제의 취소가 두 번 반영되지 않도록 취소 트랜잭션에서 쓴다.
     *
     * @param paymentId 결제 ID
     * @return 잠근 결제
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);
}
