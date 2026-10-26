package com.ticketing.api.domain.payment.repository;

import com.ticketing.api.domain.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Payment 엔티티에 대한 기본 CRUD를 제공하는 레포지토리.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
