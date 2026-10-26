package com.ticketing.api.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.payment.entity.PaymentStatus;
import com.ticketing.api.domain.payment.gateway.MockPaymentGateway;
import com.ticketing.api.domain.payment.producer.SeatEventProducer;
import com.ticketing.api.domain.payment.repository.PaymentRepository;
import com.ticketing.api.domain.payment.service.PaymentService;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 선점 → 결제 → 좌석 확정/해제까지 실제 Redis 락과 DB를 거치는 흐름 통합 테스트.
 * 결과를 결정적으로 만들기 위해 결제 게이트웨이와 Kafka Producer만 Mock으로 대체한다.
 */
@SpringBootTest
@Testcontainers
class PaymentFlowIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean
    private MockPaymentGateway paymentGateway;

    @MockitoBean
    private SeatEventProducer seatEventProducer;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private SeatService seatService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EventRepository eventRepository;

    private Long seatId;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 남긴 데이터가 섞이지 않도록 매번 비운다
        paymentRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();
        Event event = eventRepository.save(Event.create("콘서트 결제 테스트", 1, LocalDateTime.now().plusDays(1)));
        seatId = seatRepository.save(Seat.create(event, 1)).getId();
        seatService.holdSeat(seatId);
    }

    // 결제가 승인되면 좌석이 CONFIRMED가 되고 결제 기록과 이벤트가 남아야 한다
    @Test
    @DisplayName("결제 승인 시 좌석이 CONFIRMED가 되고 seat.confirmed가 발행된다")
    void pay_approved_seatConfirmed() {
        // given
        when(paymentGateway.charge()).thenReturn(true);

        // when
        PaymentResponse response = paymentService.pay(seatId);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.CONFIRMED);
        assertThat(paymentRepository.count()).isEqualTo(1);
        verify(seatEventProducer).sendConfirmed(any(SeatConfirmedEvent.class));
    }

    // 결제가 거절되면 좌석이 AVAILABLE로 돌아가 다른 사용자가 다시 선점할 수 있어야 한다
    @Test
    @DisplayName("결제 거절 시 좌석이 AVAILABLE로 돌아가 재선점할 수 있다")
    void pay_declined_seatReleasedAndHoldableAgain() {
        // given
        when(paymentGateway.charge()).thenReturn(false);

        // when
        PaymentResponse response = paymentService.pay(seatId);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        verify(seatEventProducer).sendReleased(any(SeatReleasedEvent.class));
        seatService.holdSeat(seatId);
    }

    // 이미 결제 완료된 좌석에 다시 결제를 시도하면 예외가 발생해야 한다
    @Test
    @DisplayName("이미 확정된 좌석을 다시 결제하면 예외가 발생한다")
    void pay_alreadyConfirmed_throws() {
        // given
        when(paymentGateway.charge()).thenReturn(true);
        paymentService.pay(seatId);

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> paymentService.pay(seatId))
                .isInstanceOf(CustomException.class);
    }
}
