package com.ticketing.api.domain.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.payment.entity.Payment;
import com.ticketing.api.domain.payment.entity.PaymentStatus;
import com.ticketing.api.domain.payment.gateway.MockPaymentGateway;
import com.ticketing.api.domain.payment.producer.SeatEventProducer;
import com.ticketing.api.domain.payment.repository.PaymentRepository;
import com.ticketing.api.domain.payment.service.PaymentService;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.lock.SeatLockManager;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
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

    // 결제 기록 저장 실패를 재현하기 위해 실제 Repository를 감싼 Spy로 둔다
    @MockitoSpyBean
    private PaymentRepository paymentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EntryTokenManager entryTokenManager;

    @Autowired
    private SeatLockManager seatLockManager;

    private Event event;
    private Long seatId;
    private String entryToken;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 남긴 데이터가 섞이지 않도록 매번 비운다
        paymentRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();
        event = eventRepository.save(Event.create("콘서트 결제 테스트", 1, LocalDateTime.now().plusDays(1)));
        entryToken = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();
        seatId = seatRepository.save(Seat.create(event, 1)).getId();
        seatService.holdSeat(seatId, entryToken);
    }

    // 결제가 승인되면 좌석이 CONFIRMED가 되고 결제 기록과 이벤트가 남아야 한다
    @Test
    @DisplayName("결제 승인 시 좌석이 CONFIRMED가 되고 seat.confirmed가 발행된다")
    void pay_approved_seatConfirmed() {
        // given
        when(paymentGateway.charge()).thenReturn(true);

        // when
        PaymentResponse response = paymentService.pay(seatId, entryToken);

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
        PaymentResponse response = paymentService.pay(seatId, entryToken);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        verify(seatEventProducer).sendReleased(any(SeatReleasedEvent.class));
        seatService.holdSeat(seatId, entryToken);
    }

    // 이미 결제 완료된 좌석에 다시 결제를 시도하면 예외가 발생해야 한다
    @Test
    @DisplayName("이미 확정된 좌석을 다시 결제하면 예외가 발생한다")
    void pay_alreadyConfirmed_throws() {
        // given
        when(paymentGateway.charge()).thenReturn(true);
        paymentService.pay(seatId, entryToken);

        // when & then
        assertThatThrownBy(() -> paymentService.pay(seatId, entryToken))
                .isInstanceOf(CustomException.class);
    }

    // 다른 사람의 입장 토큰으로는 결제할 수 없고, 게이트웨이도 호출되지 않아야 한다
    @Test
    @DisplayName("선점자가 아닌 토큰으로 결제하면 SEAT_HOLDER_MISMATCH 예외가 발생하고 좌석은 HELD로 남는다")
    void pay_notHolder_throws() {
        // given
        String otherToken = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();

        // when & then
        assertThatThrownBy(() -> paymentService.pay(seatId, otherToken))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_HOLDER_MISMATCH);
        verify(paymentGateway, never()).charge();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
    }

    // 같은 좌석에 결제 요청이 동시에 몰려도 확정과 결제 기록은 정확히 1건이어야 한다
    @Test
    @DisplayName("같은 좌석에 동시에 결제해도 1건만 확정된다")
    void pay_concurrentRequests_confirmedOnlyOnce() throws InterruptedException {
        // given
        when(paymentGateway.charge()).thenReturn(true);
        int requestCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        // when
        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    paymentService.pay(seatId, entryToken);
                } catch (CustomException e) {
                    // 늦게 도착한 결제는 SEAT_NOT_HELD로 실패하는 것이 정상이다
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.CONFIRMED);
        verify(seatEventProducer, times(1)).sendConfirmed(any(SeatConfirmedEvent.class));
    }

    // 결제 제한시간이 지난 선점은 TTL 스케줄러가 풀기 전이라도 결제를 시도하지 않고 거절해야 한다
    @Test
    @DisplayName("제한시간이 지난 선점은 스케줄러 복구 전이라도 SEAT_HOLD_EXPIRED로 거절된다")
    void pay_afterHoldTtl_throwsBeforeSchedulerRelease() {
        // given
        jdbcTemplate.update("update seats set held_at = ? where id = ?",
                LocalDateTime.now().minus(Seat.HOLD_TTL).minusSeconds(1), seatId);

        // when & then
        assertThatThrownBy(() -> paymentService.pay(seatId, entryToken))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_HOLD_EXPIRED);
        verify(paymentGateway, never()).charge();
    }

    // 결제 기록 저장이 실패하면 좌석 확정도 함께 롤백되어, 기록 없이 확정된 좌석이 남지 않아야 한다
    @Test
    @DisplayName("결제 기록 저장이 실패하면 좌석 확정도 롤백되어 HELD로 남는다")
    void pay_paymentSaveFails_rollsBackConfirm() {
        // given
        when(paymentGateway.charge()).thenReturn(true);
        doThrow(new IllegalStateException("결제 기록 저장 실패")).when(paymentRepository).save(any(Payment.class));

        // when & then
        assertThatThrownBy(() -> paymentService.pay(seatId, entryToken))
                .isInstanceOf(IllegalStateException.class);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        verify(seatEventProducer, never()).sendConfirmed(any(SeatConfirmedEvent.class));
    }

    // 결제 도중 TTL이 지나 다른 사람이 다시 잡은 좌석을 원래 선점자가 확정해서는 안 된다
    @Test
    @DisplayName("TTL 만료 후 다른 사용자가 다시 선점한 좌석은 원래 선점자가 확정할 수 없다")
    void confirmSeat_afterExpiredAndReheld_throws() {
        // given
        seatService.releaseExpiredSeats(LocalDateTime.now().plusMinutes(1));
        seatLockManager.forceUnlock(seatId); // 운영에서는 Redis TTL이 락을 스스로 해제한다
        String otherToken = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();
        seatService.holdSeat(seatId, otherToken);

        // when & then
        assertThatThrownBy(() -> seatService.confirmSeat(seatId, entryToken))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_HOLDER_MISMATCH);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
