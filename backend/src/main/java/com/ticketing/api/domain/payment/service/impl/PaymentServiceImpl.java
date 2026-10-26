package com.ticketing.api.domain.payment.service.impl;

import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.payment.entity.Payment;
import com.ticketing.api.domain.payment.entity.PaymentStatus;
import com.ticketing.api.domain.payment.gateway.MockPaymentGateway;
import com.ticketing.api.domain.payment.producer.SeatEventProducer;
import com.ticketing.api.domain.payment.repository.PaymentRepository;
import com.ticketing.api.domain.payment.service.PaymentService;
import com.ticketing.api.domain.seat.dto.SeatResponse;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PaymentService 구현체. 결제 게이트웨이 호출(지연 포함)은 트랜잭션 밖에서 수행하고,
 * 좌석 상태 변경과 결제 기록은 각각 짧은 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final SeatService seatService;
    private final MockPaymentGateway paymentGateway;
    private final SeatEventProducer seatEventProducer;

    @Override
    // 게이트웨이 지연 동안 DB 커넥션을 붙잡지 않도록 메서드 전체를 트랜잭션 없이 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse pay(Long seatId) {
        // 결제 전에 선점 상태를 확인해, 이미 만료/확정된 좌석에 대해 결제를 시도하지 않는다.
        SeatResponse seat = seatService.getSeat(seatId);
        if (seat.status() != SeatStatus.HELD) {
            throw new CustomException(ErrorCode.SEAT_NOT_HELD);
        }

        boolean approved = paymentGateway.charge();

        if (approved) {
            // 결제 중 TTL 스케줄러가 좌석을 해제했다면 여기서 SEAT_NOT_HELD로 실패한다 (Mock이라 환불은 없음).
            seatService.confirmSeat(seatId);
            Payment payment = paymentRepository.save(Payment.create(seatId, PaymentStatus.SUCCESS));
            seatEventProducer.sendConfirmed(SeatConfirmedEvent.of(seat.eventId(), seatId, payment.getId()));
            log.info("결제 승인 - seatId: {}, paymentId: {}", seatId, payment.getId());
            return PaymentResponse.from(payment);
        }

        seatService.releaseSeat(seatId);
        Payment payment = paymentRepository.save(Payment.create(seatId, PaymentStatus.FAILED));
        seatEventProducer.sendReleased(SeatReleasedEvent.paymentFailed(seat.eventId(), seatId, payment.getId()));
        log.info("결제 거절, 좌석 해제 - seatId: {}, paymentId: {}", seatId, payment.getId());
        return PaymentResponse.from(payment);
    }
}
