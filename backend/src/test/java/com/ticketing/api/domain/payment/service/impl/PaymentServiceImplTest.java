package com.ticketing.api.domain.payment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.payment.entity.Payment;
import com.ticketing.api.domain.payment.entity.PaymentStatus;
import com.ticketing.api.domain.payment.gateway.MockPaymentGateway;
import com.ticketing.api.domain.payment.producer.SeatEventProducer;
import com.ticketing.api.domain.payment.repository.PaymentRepository;
import com.ticketing.api.domain.seat.dto.SeatResponse;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PaymentServiceImpl 단위 테스트. 게이트웨이/Kafka/Repository는 Mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Long SEAT_ID = 10L;
    private static final Long EVENT_ID = 1L;
    private static final String TOKEN = "entry-token";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SeatService seatService;

    @Mock
    private MockPaymentGateway paymentGateway;

    @Mock
    private SeatEventProducer seatEventProducer;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // 결제가 승인되면 좌석이 확정되고 seat.confirmed 이벤트가 발행되는지 확인
    @Test
    @DisplayName("결제가 승인되면 좌석을 확정하고 seat.confirmed를 발행한다")
    void pay_approved_confirmsSeat() {
        // given
        when(seatService.getHeldSeat(SEAT_ID, TOKEN)).thenReturn(seat(SeatStatus.HELD));
        when(paymentGateway.charge()).thenReturn(true);
        runCallbacksDirectly();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PaymentResponse response = paymentService.pay(SEAT_ID, TOKEN);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(seatService).confirmSeat(SEAT_ID, TOKEN);
        verify(seatService, never()).releaseSeat(SEAT_ID, TOKEN);
        verify(seatEventProducer).sendConfirmed(any(SeatConfirmedEvent.class));
    }

    // 결제가 거절되면 좌석이 해제되고 seat.released 이벤트가 발행되는지 확인
    @Test
    @DisplayName("결제가 거절되면 좌석을 해제하고 seat.released를 발행한다")
    void pay_declined_releasesSeat() {
        // given
        when(seatService.getHeldSeat(SEAT_ID, TOKEN)).thenReturn(seat(SeatStatus.HELD));
        when(paymentGateway.charge()).thenReturn(false);
        runCallbacksDirectly();
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PaymentResponse response = paymentService.pay(SEAT_ID, TOKEN);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(seatService).releaseSeat(SEAT_ID, TOKEN);
        verify(seatService, never()).confirmSeat(SEAT_ID, TOKEN);
        verify(seatEventProducer).sendReleased(any(SeatReleasedEvent.class));
    }

    // 선점 상태가 아닌 좌석은 결제 게이트웨이를 호출하기 전에 거절되어야 한다
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

    // 트랜잭션 없이 콜백만 바로 실행해서, 트랜잭션 안에서 호출되는 협력 객체를 검증할 수 있게 한다
    private void runCallbacksDirectly() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    }

    private SeatResponse seat(SeatStatus status) {
        return new SeatResponse(SEAT_ID, EVENT_ID, 1, status);
    }
}
