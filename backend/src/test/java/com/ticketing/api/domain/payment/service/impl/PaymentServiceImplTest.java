package com.ticketing.api.domain.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.outbox.service.OutboxService;
import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.payment.entity.Payment;
import com.ticketing.api.domain.payment.entity.PaymentStatus;
import com.ticketing.api.domain.payment.gateway.ChargeResult;
import com.ticketing.api.domain.payment.gateway.MockPaymentGateway;
import com.ticketing.api.domain.payment.repository.PaymentRepository;
import com.ticketing.api.domain.seat.dto.SeatResponse;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import com.ticketing.api.global.lock.RedisLock;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PaymentServiceImpl 단위 테스트. 게이트웨이/Kafka(outbox)/Repository/Redis 락은 Mock으로 대체한다.
 */
class PaymentServiceImplTest {

    private static final Long SEAT_ID = 10L;
    private static final Long EVENT_ID = 1L;
    private static final String TOKEN = "entry-token";
    private static final String LOCK_KEY = "payment:lock:" + SEAT_ID;
    private static final String LOCK_TOKEN = "lock-token";
    private static final String APPROVAL_ID = "approval-1";
    private static final Long PAYMENT_ID = 100L;

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final SeatService seatService = mock(SeatService.class);
    private final MockPaymentGateway paymentGateway = mock(MockPaymentGateway.class);
    private final OutboxService outboxService = mock(OutboxService.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final RedisLock redisLock = mock(RedisLock.class);
    private final PaymentServiceImpl paymentService = new PaymentServiceImpl(paymentRepository, seatService,
            paymentGateway, outboxService, transactionTemplate, redisLock, 30000);

    @BeforeEach
    void setUp() {
        // 기본은 락 획득 성공, 트랜잭션은 콜백만 바로 실행한다 (트랜잭션 안에서 호출되는 협력 객체를 검증하기 위해)
        lenient().when(redisLock.tryLock(eq(LOCK_KEY), any())).thenReturn(LOCK_TOKEN);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        lenient().doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        lenient().when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // 결제가 승인되면 좌석이 확정되고 seat.confirmed 이벤트가 outbox에 기록되며 결제 락이 풀려야 한다
    @Test
    @DisplayName("결제가 승인되면 좌석을 확정하고 seat.confirmed를 outbox에 기록한다")
    void pay_approved_confirmsSeat() {
        // given
        when(seatService.getHeldSeat(SEAT_ID, TOKEN)).thenReturn(seat());
        when(paymentGateway.charge()).thenReturn(ChargeResult.approved(APPROVAL_ID));

        // when
        PaymentResponse response = paymentService.pay(SEAT_ID, TOKEN);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(seatService).confirmSeat(SEAT_ID, TOKEN);
        verify(seatService, never()).releaseSeat(SEAT_ID, TOKEN);
        verify(paymentRepository).save(argThat(p -> APPROVAL_ID.equals(p.getApprovalId())));
        verify(outboxService).append(eq(KafkaTopicConfig.SEAT_CONFIRMED), eq(String.valueOf(EVENT_ID)), any(SeatConfirmedEvent.class));
        verify(redisLock).unlock(LOCK_KEY, LOCK_TOKEN);
    }

    // 결제가 거절되면 좌석이 해제되고 seat.released 이벤트가 outbox에 기록되는지 확인
    @Test
    @DisplayName("결제가 거절되면 좌석을 해제하고 seat.released를 outbox에 기록한다")
    void pay_declined_releasesSeat() {
        // given
        when(seatService.getHeldSeat(SEAT_ID, TOKEN)).thenReturn(seat());
        when(paymentGateway.charge()).thenReturn(ChargeResult.declined());

        // when
        PaymentResponse response = paymentService.pay(SEAT_ID, TOKEN);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(seatService).releaseSeat(SEAT_ID, TOKEN);
        verify(seatService, never()).confirmSeat(SEAT_ID, TOKEN);
        verify(outboxService).append(eq(KafkaTopicConfig.SEAT_RELEASED), eq(String.valueOf(EVENT_ID)), any(SeatReleasedEvent.class));
        verify(redisLock).unlock(LOCK_KEY, LOCK_TOKEN);
    }

    // 같은 좌석의 결제가 진행 중이면 게이트웨이를 호출하지 않고 바로 거절해야 한다 (승인 중복 방지)
    @Test
    @DisplayName("같은 좌석의 결제가 진행 중이면 PAYMENT_IN_PROGRESS로 거절하고 결제를 시도하지 않는다")
    void pay_lockedByAnotherPayment_throwsWithoutCharging() {
        // given
        when(redisLock.tryLock(eq(LOCK_KEY), any())).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> paymentService.pay(SEAT_ID, TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        verify(paymentGateway, never()).charge();
        verify(redisLock, never()).unlock(any(), any());
    }

    // 선점 상태가 아닌 좌석은 결제 게이트웨이를 호출하기 전에 거절되고, 잡았던 결제 락은 풀려야 한다
    @Test
    @DisplayName("선점 상태가 아닌 좌석은 결제를 시도하지 않고 SEAT_NOT_HELD 예외가 발생한다")
    void pay_seatNotHeld_throwsWithoutCharging() {
        // given
        when(seatService.getHeldSeat(SEAT_ID, TOKEN)).thenThrow(new CustomException(ErrorCode.SEAT_NOT_HELD));

        // when & then
        assertThatThrownBy(() -> paymentService.pay(SEAT_ID, TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_NOT_HELD);
        verify(paymentGateway, never()).charge();
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(redisLock).unlock(LOCK_KEY, LOCK_TOKEN);
    }

    // 다른 사람이 선점한 좌석은 결제 게이트웨이를 호출하기 전에 거절되어야 한다
    @Test
    @DisplayName("선점자가 아니면 결제를 시도하지 않고 SEAT_HOLDER_MISMATCH 예외가 발생한다")
    void pay_notHolder_throwsWithoutCharging() {
        // given
        when(seatService.getHeldSeat(SEAT_ID, TOKEN)).thenThrow(new CustomException(ErrorCode.SEAT_HOLDER_MISMATCH));

        // when & then
        assertThatThrownBy(() -> paymentService.pay(SEAT_ID, TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_HOLDER_MISMATCH);
        verify(paymentGateway, never()).charge();
    }

    // 승인 후 확정에 실패하면 돈만 빠져나가지 않도록 승인을 취소하고 CANCELLED로 기록한 뒤 원래 예외를 던져야 한다
    @Test
    @DisplayName("승인 후 좌석 확정에 실패하면 승인을 취소하고 CANCELLED로 기록한다")
    void pay_confirmFailsAfterApproval_cancelsApproval() {
        // given
        when(seatService.getHeldSeat(SEAT_ID, TOKEN)).thenReturn(seat());
        when(paymentGateway.charge()).thenReturn(ChargeResult.approved(APPROVAL_ID));
        doThrow(new CustomException(ErrorCode.SEAT_HOLD_EXPIRED)).when(seatService).confirmSeat(SEAT_ID, TOKEN);

        // when & then
        assertThatThrownBy(() -> paymentService.pay(SEAT_ID, TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_HOLD_EXPIRED);
        verify(paymentGateway).cancel(APPROVAL_ID);
        verify(paymentRepository).save(argThat(p -> p.getStatus() == PaymentStatus.CANCELLED
                && APPROVAL_ID.equals(p.getApprovalId())));
        verify(outboxService, never()).append(any(), any(), any());
        verify(redisLock).unlock(LOCK_KEY, LOCK_TOKEN);
    }

    // 승인 취소 자체가 실패해도 확정 실패의 원래 예외를 가리지 않아야 한다 (취소 실패는 로그로 남긴다)
    @Test
    @DisplayName("승인 취소가 실패해도 확정 실패의 원래 예외를 그대로 던진다")
    void pay_cancelFails_keepsOriginalException() {
        // given
        when(seatService.getHeldSeat(SEAT_ID, TOKEN)).thenReturn(seat());
        when(paymentGateway.charge()).thenReturn(ChargeResult.approved(APPROVAL_ID));
        doThrow(new CustomException(ErrorCode.SEAT_NOT_HELD)).when(seatService).confirmSeat(SEAT_ID, TOKEN);
        doThrow(new IllegalStateException("gateway down")).when(paymentGateway).cancel(APPROVAL_ID);

        // when & then
        assertThatThrownBy(() -> paymentService.pay(SEAT_ID, TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_NOT_HELD);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // 예매를 취소하면 환불 후 결제가 REFUNDED가 되고, 좌석이 다시 열리며 seat.released(BOOKING_CANCELLED)가 기록되어야 한다
    @Test
    @DisplayName("예매 취소 시 환불하고 좌석을 다시 열며 seat.released를 outbox에 기록한다")
    void cancel_refundsAndReopensSeat() {
        // given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));
        when(seatService.reopenSeat(SEAT_ID)).thenReturn(new SeatResponse(SEAT_ID, EVENT_ID, 1, SeatStatus.AVAILABLE));

        // when
        PaymentResponse response = paymentService.cancel(PAYMENT_ID, TOKEN);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentGateway).cancel(APPROVAL_ID);
        verify(seatService).reopenSeat(SEAT_ID);
        verify(outboxService).append(eq(KafkaTopicConfig.SEAT_RELEASED), eq(String.valueOf(EVENT_ID)),
                argThat(e -> e instanceof SeatReleasedEvent released
                        && SeatReleasedEvent.REASON_BOOKING_CANCELLED.equals(released.reason())
                        && PAYMENT_ID.equals(released.paymentId())));
        verify(redisLock).unlock(LOCK_KEY, LOCK_TOKEN);
    }

    // 결제한 사람이 아니면 환불을 요청하기 전에 거절하고, 잡았던 락은 풀어야 한다
    @Test
    @DisplayName("결제한 사용자가 아니면 환불하지 않고 PAYMENT_PAYER_MISMATCH 예외가 발생한다")
    void cancel_notPayer_throwsWithoutRefund() {
        // given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));

        // when & then
        assertThatThrownBy(() -> paymentService.cancel(PAYMENT_ID, "other-token"))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_PAYER_MISMATCH);
        verify(paymentGateway, never()).cancel(any());
        verify(seatService, never()).reopenSeat(any());
        verify(redisLock).unlock(LOCK_KEY, LOCK_TOKEN);
    }

    // 승인된 결제가 아니면(이미 취소됨, 거절됨) 환불을 다시 요청하지 않아야 한다
    @Test
    @DisplayName("이미 취소된 결제는 환불하지 않고 PAYMENT_NOT_CANCELLABLE 예외가 발생한다")
    void cancel_alreadyRefunded_throwsWithoutRefund() {
        // given
        Payment refunded = paidPayment();
        refunded.refund(TOKEN);
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(refunded));

        // when & then
        assertThatThrownBy(() -> paymentService.cancel(PAYMENT_ID, TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_CANCELLABLE);
        verify(paymentGateway, never()).cancel(any());
    }

    // 같은 좌석의 결제나 취소가 진행 중이면 환불을 요청하지 않고 바로 거절해야 한다 (환불 중복 방지)
    @Test
    @DisplayName("같은 좌석의 취소가 진행 중이면 PAYMENT_IN_PROGRESS로 거절하고 환불하지 않는다")
    void cancel_lockedByAnotherRequest_throwsWithoutRefund() {
        // given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));
        when(redisLock.tryLock(eq(LOCK_KEY), any())).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> paymentService.cancel(PAYMENT_ID, TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        verify(paymentGateway, never()).cancel(any());
        verify(redisLock, never()).unlock(any(), any());
    }

    // 환불이 실패하면 예매를 취소하지 않아야 사용자가 좌석과 돈을 모두 잃지 않고 다시 시도할 수 있다
    @Test
    @DisplayName("환불이 실패하면 좌석과 결제를 그대로 두고 예외를 던진다")
    void cancel_refundFails_keepsBooking() {
        // given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(paidPayment()));
        doThrow(new IllegalStateException("gateway down")).when(paymentGateway).cancel(APPROVAL_ID);

        // when & then
        assertThatThrownBy(() -> paymentService.cancel(PAYMENT_ID, TOKEN))
                .isInstanceOf(IllegalStateException.class);
        verify(seatService, never()).reopenSeat(any());
        verify(outboxService, never()).append(any(), any(), any());
        verify(redisLock).unlock(LOCK_KEY, LOCK_TOKEN);
    }

    // 없는 결제는 락을 잡기 전에 거절해야 한다
    @Test
    @DisplayName("없는 결제를 취소하면 PAYMENT_NOT_FOUND 예외가 발생한다")
    void cancel_notFound_throws() {
        // given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> paymentService.cancel(PAYMENT_ID, TOKEN))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
        verify(redisLock, never()).tryLock(any(), any());
    }

    private SeatResponse seat() {
        return new SeatResponse(SEAT_ID, EVENT_ID, 1, SeatStatus.HELD);
    }

    // DB에서 읽을 때마다 새 객체가 오는 것처럼, 호출마다 새 결제 객체를 만든다
    private Payment paidPayment() {
        return Payment.create(SEAT_ID, PaymentStatus.SUCCESS, APPROVAL_ID, TOKEN);
    }
}
