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
import com.ticketing.api.domain.outbox.entity.OutboxEvent;
import com.ticketing.api.domain.outbox.repository.OutboxEventRepository;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.entity.Payment;
import com.ticketing.api.domain.payment.entity.PaymentStatus;
import com.ticketing.api.domain.payment.gateway.ChargeResult;
import com.ticketing.api.domain.payment.gateway.MockPaymentGateway;
import com.ticketing.api.domain.payment.repository.PaymentRepository;
import com.ticketing.api.domain.payment.service.PaymentService;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.lock.SeatLockManager;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.scheduler.SeatHoldExpirationScheduler;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
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
 * 결과를 결정적으로 만들기 위해 결제 게이트웨이만 Mock으로 대체하고, 발행할 이벤트는 outbox에 기록된 행으로 확인한다.
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

    // 선점 시각을 과거로 돌리는 테스트 도중 TTL 복구가 먼저 좌석을 풀어 결과가 달라지지 않도록 스케줄러를 대체한다
    @MockitoBean
    private SeatHoldExpirationScheduler seatHoldExpirationScheduler;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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
        outboxEventRepository.deleteAll();
        seatRepository.deleteAll();
        eventRepository.deleteAll();
        event = eventRepository.save(Event.create("콘서트 결제 테스트", 1, LocalDateTime.now().plusDays(1)));
        entryToken = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();
        seatId = seatRepository.save(Seat.create(event, 1)).getId();
        seatService.holdSeat(seatId, entryToken);
    }

    // 결제가 승인되면 좌석이 CONFIRMED가 되고 결제 기록과 이벤트가 남아야 한다
    @Test
    @DisplayName("결제 승인 시 좌석이 CONFIRMED가 되고 seat.confirmed가 outbox에 기록된다")
    void pay_approved_seatConfirmed() {
        // given
        when(paymentGateway.charge()).thenReturn(ChargeResult.approved("approval-1"));

        // when
        PaymentResponse response = paymentService.pay(seatId, entryToken);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.CONFIRMED);
        assertThat(paymentRepository.count()).isEqualTo(1);
        List<OutboxEvent> outbox = outboxEventRepository.findAll();
        assertThat(outbox).singleElement().satisfies(event -> {
            assertThat(event.getTopic()).isEqualTo(KafkaTopicConfig.SEAT_CONFIRMED);
            assertThat(event.getMessageKey()).isEqualTo(String.valueOf(this.event.getId()));
            assertThat(event.getPayload()).contains("\"paymentId\":" + response.id());
            assertThat(event.getPublishedAt()).isNull();
        });
    }

    // 결제가 거절되면 좌석이 AVAILABLE로 돌아가 다른 사용자가 다시 선점할 수 있어야 한다
    @Test
    @DisplayName("결제 거절 시 좌석이 AVAILABLE로 돌아가 재선점할 수 있다")
    void pay_declined_seatReleasedAndHoldableAgain() {
        // given
        when(paymentGateway.charge()).thenReturn(ChargeResult.declined());

        // when
        PaymentResponse response = paymentService.pay(seatId, entryToken);

        // then
        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(outboxEventRepository.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getTopic()).isEqualTo(KafkaTopicConfig.SEAT_RELEASED);
            assertThat(event.getPayload()).contains("PAYMENT_FAILED", "\"paymentId\":" + response.id());
        });
        seatService.holdSeat(seatId, entryToken);
    }

    // 이미 결제 완료된 좌석에 다시 결제를 시도하면 예외가 발생해야 한다
    @Test
    @DisplayName("이미 확정된 좌석을 다시 결제하면 예외가 발생한다")
    void pay_alreadyConfirmed_throws() {
        // given
        when(paymentGateway.charge()).thenReturn(ChargeResult.approved("approval-1"));
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

    // 같은 좌석에 결제 요청이 동시에 몰려도 게이트웨이 승인 요청, 확정, 결제 기록은 정확히 1건이어야 한다
    @Test
    @DisplayName("같은 좌석에 동시에 결제해도 게이트웨이 승인은 1번만 요청되고 1건만 확정된다")
    void pay_concurrentRequests_confirmedOnlyOnce() throws InterruptedException {
        // given
        // 실제 게이트웨이처럼 응답에 시간이 걸려야 요청들이 결제 단계에서 겹친다
        when(paymentGateway.charge()).thenAnswer(invocation -> {
            Thread.sleep(200);
            return ChargeResult.approved("approval-1");
        });
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
        assertThat(outboxEventRepository.findAll()).singleElement()
                .extracting(OutboxEvent::getTopic).isEqualTo(KafkaTopicConfig.SEAT_CONFIRMED);
        verify(paymentGateway, times(1)).charge();
        verify(paymentGateway, never()).cancel(any());
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
    @DisplayName("결제 기록 저장이 실패하면 좌석 확정과 outbox 기록도 롤백되어 HELD로 남는다")
    void pay_paymentSaveFails_rollsBackConfirm() {
        // given
        when(paymentGateway.charge()).thenReturn(ChargeResult.approved("approval-1"));
        doThrow(new IllegalStateException("결제 기록 저장 실패")).when(paymentRepository).save(any(Payment.class));

        // when & then
        assertThatThrownBy(() -> paymentService.pay(seatId, entryToken))
                .isInstanceOf(IllegalStateException.class);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(outboxEventRepository.count()).isZero();
        // 확정이 롤백되었으므로 승인된 결제는 취소되어야 한다
        verify(paymentGateway).cancel("approval-1");
    }

    // 결제 승인을 기다리는 사이 제한시간이 지나 확정에 실패하면, 승인을 취소하고 CANCELLED로 남겨야 한다
    @Test
    @DisplayName("결제 중 선점이 만료되어 확정에 실패하면 승인을 취소하고 CANCELLED로 기록한다")
    void pay_holdExpiresDuringCharge_cancelsApproval() {
        // given
        when(paymentGateway.charge()).thenAnswer(invocation -> {
            // 게이트웨이 응답을 기다리는 동안 제한시간이 지난 상황을 만든다
            jdbcTemplate.update("update seats set held_at = ? where id = ?",
                    LocalDateTime.now().minus(Seat.HOLD_TTL).minusSeconds(1), seatId);
            return ChargeResult.approved("approval-late");
        });

        // when & then
        assertThatThrownBy(() -> paymentService.pay(seatId, entryToken))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_HOLD_EXPIRED);
        verify(paymentGateway).cancel("approval-late");
        assertThat(paymentRepository.findAll()).singleElement().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(payment.getApprovalId()).isEqualTo("approval-late");
        });
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(outboxEventRepository.count()).isZero();
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
