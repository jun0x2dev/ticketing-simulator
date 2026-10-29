package com.ticketing.api.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import com.ticketing.api.domain.outbox.entity.OutboxEvent;
import com.ticketing.api.domain.outbox.repository.OutboxEventRepository;
import com.ticketing.api.domain.outbox.service.OutboxService;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.seat.scheduler.SeatHoldExpirationScheduler;
import com.ticketing.api.global.config.KafkaTopicConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 DB(H2)로 outbox 기록 → 행 잠금 조회 → 발행 완료 표시 → 정리까지의 흐름을 검증한다.
 * 브로커 장애와 복구를 결정적으로 재현하기 위해 KafkaTemplate만 Mock으로 대체한다.
 */
@SpringBootTest
class OutboxIntegrationTest {

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    // 같은 DB를 쓰는 TTL 복구가 테스트 도중 outbox 행을 추가하지 않도록 스케줄러를 대체한다
    @MockitoBean
    private SeatHoldExpirationScheduler seatHoldExpirationScheduler;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
    }

    // 비즈니스 트랜잭션 밖에서 기록하면 원자성이 깨지므로 예외로 막아야 한다
    @Test
    @DisplayName("트랜잭션 밖에서 append하면 예외가 발생하고 아무것도 기록되지 않는다")
    void append_withoutTransaction_throws() {
        // when & then
        assertThatThrownBy(() -> outboxService.append(KafkaTopicConfig.SEAT_CONFIRMED, "1", SeatConfirmedEvent.of(1L, 1L, 1L)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(outboxEventRepository.count()).isZero();
    }

    // 호출한 쪽 트랜잭션이 롤백되면 기록한 이벤트도 함께 사라져야 한다
    @Test
    @DisplayName("호출한 트랜잭션이 롤백되면 outbox 기록도 사라진다")
    void append_rolledBackWithCaller() {
        // when
        transactionTemplate.executeWithoutResult(status -> {
            outboxService.append(KafkaTopicConfig.SEAT_CONFIRMED, "1", SeatConfirmedEvent.of(1L, 1L, 1L));
            status.setRollbackOnly();
        });

        // then
        assertThat(outboxEventRepository.count()).isZero();
    }

    // 브로커가 죽어 있는 동안은 발행 대기로 남고, 복구되면 다음 주기에 발행 완료가 되어야 한다
    @Test
    @DisplayName("브로커 장애 중에는 발행 대기로 남았다가 복구 후 발행된다")
    void relayPending_brokerDownThenRecovered() {
        // given
        appendConfirmed("1");
        appendConfirmed("2");
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .when(kafkaTemplate).send(any(), any(), any());

        // when
        int publishedWhileDown = outboxService.relayPending(100);

        // then
        assertThat(publishedWhileDown).isZero();
        assertThat(outboxEventRepository.findAll())
                .allSatisfy(event -> {
                    assertThat(event.getPublishedAt()).isNull();
                    assertThat(event.getAttempts()).isEqualTo(1);
                });

        // when
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(any(), any(), any());
        int publishedAfterRecovery = outboxService.relayPending(100);

        // then
        assertThat(publishedAfterRecovery).isEqualTo(2);
        assertThat(outboxEventRepository.findAll()).allSatisfy(event -> assertThat(event.getPublishedAt()).isNotNull());
        assertThat(outboxService.relayPending(100)).isZero();
    }

    // 한 번에 가져오는 개수를 지키고, 기록된 순서(오래된 것)부터 발행해야 한다
    @Test
    @DisplayName("발행 대기 이벤트를 기록된 순서대로 묶음 크기만큼만 발행한다")
    void relayPending_respectsOrderAndBatchSize() {
        // given
        appendConfirmed("1");
        appendConfirmed("2");
        appendConfirmed("3");
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(any(), any(), any());

        // when
        int published = outboxService.relayPending(2);

        // then
        assertThat(published).isEqualTo(2);
        List<OutboxEvent> events = outboxEventRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();
        assertThat(events).extracting(OutboxEvent::getMessageKey).containsExactly("1", "2", "3");
        assertThat(events.get(0).getPublishedAt()).isNotNull();
        assertThat(events.get(1).getPublishedAt()).isNotNull();
        assertThat(events.get(2).getPublishedAt()).isNull();
    }

    // 보관 기간이 지난 발행 완료 이벤트만 지우고, 발행 대기와 최근 발행 이벤트는 남겨야 한다
    @Test
    @DisplayName("보관 기간이 지난 발행 완료 이벤트만 정리한다")
    void deletePublishedBefore_deletesOnlyOldPublished() {
        // given
        appendConfirmed("old");
        appendConfirmed("recent");
        appendConfirmed("pending");
        jdbcTemplate.update("update outbox_events set published_at = ? where message_key = 'old'", LocalDateTime.now().minusDays(2));
        jdbcTemplate.update("update outbox_events set published_at = ? where message_key = 'recent'", LocalDateTime.now());

        // when
        int deleted = outboxService.deletePublishedBefore(LocalDateTime.now().minusDays(1), 100);

        // then
        assertThat(deleted).isEqualTo(1);
        assertThat(outboxEventRepository.findAll()).extracting(OutboxEvent::getMessageKey)
                .containsExactlyInAnyOrder("recent", "pending");
    }

    private void appendConfirmed(String key) {
        transactionTemplate.executeWithoutResult(status ->
                outboxService.append(KafkaTopicConfig.SEAT_CONFIRMED, key, SeatConfirmedEvent.of(1L, 1L, 1L)));
    }
}
