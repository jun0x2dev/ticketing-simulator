package com.ticketing.api.domain.payment.service.impl;

import com.ticketing.api.domain.outbox.service.OutboxService;
import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.payment.entity.Payment;
import com.ticketing.api.domain.payment.entity.PaymentStatus;
import com.ticketing.api.domain.payment.gateway.ChargeResult;
import com.ticketing.api.domain.payment.gateway.MockPaymentGateway;
import com.ticketing.api.domain.payment.repository.PaymentRepository;
import com.ticketing.api.domain.payment.service.PaymentService;
import com.ticketing.api.domain.seat.dto.SeatResponse;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import com.ticketing.api.global.lock.RedisLock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PaymentService 구현체. 결제 게이트웨이 호출(지연 포함)은 트랜잭션 밖에서 수행하고,
 * 좌석 상태 변경, 결제 기록, 발행할 이벤트(outbox)는 하나의 짧은 트랜잭션으로 묶어 처리한다.
 * 같은 좌석의 결제는 Redis 락으로 한 번에 하나만 진행해, 게이트웨이 승인이 중복으로 나가지 않게 한다.
 */
@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final String LOCK_KEY_PREFIX = "payment:lock:";

    private final PaymentRepository paymentRepository;
    private final SeatService seatService;
    private final MockPaymentGateway paymentGateway;
    private final OutboxService outboxService;
    // 게이트웨이 호출 이후의 DB 작업만 트랜잭션으로 묶기 위해 선언적 @Transactional 대신 사용한다.
    private final TransactionTemplate transactionTemplate;
    private final RedisLock redisLock;
    private final Duration lockTtl;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              SeatService seatService,
                              MockPaymentGateway paymentGateway,
                              OutboxService outboxService,
                              TransactionTemplate transactionTemplate,
                              RedisLock redisLock,
                              @Value("${payment.lock-ttl-ms:30000}") long lockTtlMillis) {
        this.paymentRepository = paymentRepository;
        this.seatService = seatService;
        this.paymentGateway = paymentGateway;
        this.outboxService = outboxService;
        this.transactionTemplate = transactionTemplate;
        this.redisLock = redisLock;
        this.lockTtl = Duration.ofMillis(lockTtlMillis);
    }

    @Override
    // 게이트웨이 지연 동안 DB 커넥션을 붙잡지 않도록 메서드 전체를 트랜잭션 없이 실행한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse pay(Long seatId, String entryToken) {
        // 확정 단계의 행 잠금은 좌석이 두 번 확정되는 것만 막는다. 게이트웨이 승인 자체가 중복으로 나가지 않도록
        // 승인 요청 전에 좌석 단위로 결제를 하나만 진행시킨다. TTL은 게이트웨이 최대 응답 시간보다 넉넉히 둔다.
        String lockKey = LOCK_KEY_PREFIX + seatId;
        String lockToken = redisLock.tryLock(lockKey, lockTtl);
        if (lockToken == null) {
            throw new CustomException(ErrorCode.PAYMENT_IN_PROGRESS);
        }
        try {
            // 락을 잡은 뒤 확인해야, 앞선 결제가 막 확정하고 락을 놓은 경우를 SEAT_NOT_HELD로 걸러낸다.
            SeatResponse seat = seatService.getHeldSeat(seatId, entryToken);
            ChargeResult charge = paymentGateway.charge();
            return charge.approved()
                    ? confirm(seat, entryToken, charge)
                    : decline(seat, entryToken);
        } finally {
            redisLock.unlock(lockKey, lockToken);
        }
    }

    // 좌석 확정, 결제 기록, seat.confirmed 이벤트를 한 트랜잭션으로 묶는다. 커밋되면 이벤트도 반드시 발행되고,
    // 롤백되면 셋 다 남지 않는다. 결제 중 선점이 만료되었거나 상태가 바뀌어 확정에 실패하면 승인을 취소한다.
    private PaymentResponse confirm(SeatResponse seat, String entryToken, ChargeResult charge) {
        Long seatId = seat.id();
        Payment payment;
        try {
            payment = transactionTemplate.execute(status -> {
                seatService.confirmSeat(seatId, entryToken);
                Payment saved = paymentRepository.save(Payment.create(seatId, PaymentStatus.SUCCESS, charge.approvalId()));
                outboxService.append(KafkaTopicConfig.SEAT_CONFIRMED, String.valueOf(seat.eventId()),
                        SeatConfirmedEvent.of(seat.eventId(), seatId, saved.getId()));
                return saved;
            });
        } catch (RuntimeException e) {
            cancelApproval(seatId, charge.approvalId());
            throw e;
        }
        log.info("결제 승인 - seatId: {}, paymentId: {}", seatId, payment.getId());
        return PaymentResponse.from(payment);
    }

    private PaymentResponse decline(SeatResponse seat, String entryToken) {
        Long seatId = seat.id();
        Payment payment = transactionTemplate.execute(status -> {
            seatService.releaseSeat(seatId, entryToken);
            Payment saved = paymentRepository.save(Payment.create(seatId, PaymentStatus.FAILED, null));
            outboxService.append(KafkaTopicConfig.SEAT_RELEASED, String.valueOf(seat.eventId()),
                    SeatReleasedEvent.paymentFailed(seat.eventId(), seatId, saved.getId()));
            return saved;
        });
        log.info("결제 거절, 좌석 해제 - seatId: {}, paymentId: {}", seatId, payment.getId());
        return PaymentResponse.from(payment);
    }

    // 승인된 돈이 좌석 없이 빠져나가지 않도록 승인을 취소하고 기록을 남긴다. 확정 실패의 원래 예외를 가리지 않도록
    // 취소나 기록이 실패해도 예외를 던지지 않고, 사람이 처리할 수 있게 승인 번호와 함께 error 로그를 남긴다.
    private void cancelApproval(Long seatId, String approvalId) {
        try {
            paymentGateway.cancel(approvalId);
        } catch (RuntimeException e) {
            log.error("결제 승인 취소 실패, 수동 처리 필요 - seatId: {}, approvalId: {}", seatId, approvalId, e);
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status ->
                    paymentRepository.save(Payment.create(seatId, PaymentStatus.CANCELLED, approvalId)));
        } catch (RuntimeException e) {
            log.error("결제 취소 기록 실패 - seatId: {}, approvalId: {}", seatId, approvalId, e);
            return;
        }
        log.warn("좌석 확정 실패로 결제 승인 취소 - seatId: {}, approvalId: {}", seatId, approvalId);
    }
}
