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

/**
 * PaymentServiceImpl 단위 테스트. 게이트웨이/Kafka/Repository는 Mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final Long SEAT_ID = 10L;
    private static final Long EVENT_ID = 1L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SeatService seatService;

    @Mock
    private MockPaymentGateway paymentGateway;

    @Mock
    private SeatEventProducer seatEventProducer;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    // 결제가 승인되면 좌석이 확정되고 seat.confirmed 이벤트가 발행되는지 확인
    @Test
    @DisplayName("결제가 승인되면 좌석을 확정하고 seat.confirmed를 발행한다")
    void pay_approved_confirmsSeat() {
        // given
        when(seatService.getSeat(SEAT_ID)).thenReturn(seat(SeatStatus.HELD));
        when(paymentGateway.charge()).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PaymentResponse response = paymentService.pay(SEAT_ID);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(seatService).confirmSeat(SEAT_ID);
        verify(seatService, never()).releaseSeat(SEAT_ID);
        verify(seatEventProducer).sendConfirmed(any(SeatConfirmedEvent.class));
    }

    // 결제가 거절되면 좌석이 해제되고 seat.released 이벤트가 발행되는지 확인
    @Test
    @DisplayName("결제가 거절되면 좌석을 해제하고 seat.released를 발행한다")
    void pay_declined_releasesSeat() {
        // given
        when(seatService.getSeat(SEAT_ID)).thenReturn(seat(SeatStatus.HELD));
        when(paymentGateway.charge()).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PaymentResponse response = paymentService.pay(SEAT_ID);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        verify(seatService).releaseSeat(SEAT_ID);
        verify(seatService, never()).confirmSeat(SEAT_ID);
        verify(seatEventProducer).sendReleased(any(SeatReleasedEvent.class));
    }

    // 선점 상태가 아닌 좌석은 결제 게이트웨이를 호출하기 전에 거절되어야 한다
    @Test
    @DisplayName("선점 상태가 아닌 좌석은 결제를 시도하지 않고 SEAT_NOT_HELD 예외가 발생한다")
    void pay_seatNotHeld_throwsWithoutCharging() {
        // given
        when(seatService.getSeat(SEAT_ID)).thenReturn(seat(SeatStatus.AVAILABLE));

        // when & then
        assertThatThrownBy(() -> paymentService.pay(SEAT_ID))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_NOT_HELD);
        verify(paymentGateway, never()).charge();
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    private SeatResponse seat(SeatStatus status) {
        return new SeatResponse(SEAT_ID, EVENT_ID, 1, status);
    }
}
