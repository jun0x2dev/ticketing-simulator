package com.ticketing.api.domain.outbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.outbox.entity.OutboxEvent;
import com.ticketing.api.domain.outbox.repository.OutboxEventRepository;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * OutboxServiceImpl 단위 테스트. 브로커 응답(성공/실패/무응답)에 따라 발행 완료 표시가 정확히 갈리는지 검증한다.
 */
class OutboxServiceImplTest {

    private static final long SEND_TIMEOUT_MILLIS = 200;

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final OutboxServiceImpl outboxService =
            new OutboxServiceImpl(repository, kafkaTemplate, jsonMapper, SEND_TIMEOUT_MILLIS);

    // 기록되는 행에 토픽/키와 함께 원래 DTO로 되돌릴 수 있는 타입과 JSON이 남아야 한다
    @Test
    @DisplayName("append는 payload의 타입 이름과 JSON을 함께 기록한다")
    void append_storesTypeAndJson() {
        // given
        SeatConfirmedEvent payload = SeatConfirmedEvent.of(1L, 10L, 100L);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        // when
        outboxService.append("seat.confirmed", "1", payload);

        // then
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo("seat.confirmed");
        assertThat(saved.getMessageKey()).isEqualTo("1");
        assertThat(saved.getPayloadType()).isEqualTo(SeatConfirmedEvent.class.getName());
        assertThat(jsonMapper.readValue(saved.getPayload(), SeatConfirmedEvent.class)).isEqualTo(payload);
        assertThat(saved.getPublishedAt()).isNull();
    }

    // 브로커가 확인한 건만 발행 완료가 되고, 실패한 건은 대기 상태로 남아 실패 횟수가 늘어야 한다
    @Test
    @DisplayName("발행에 성공한 건만 완료로 표시하고 실패한 건은 다음 주기를 위해 남긴다")
    void relayPending_marksOnlyAcknowledgedEvents() {
        // given
        OutboxEvent ok = event("1", SeatConfirmedEvent.of(1L, 10L, 100L));
        OutboxEvent broken = event("2", SeatConfirmedEvent.of(2L, 20L, 200L));
        when(repository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of(ok, broken));
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(eq("seat.confirmed"), eq("1"), any());
        doReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")))
                .when(kafkaTemplate).send(eq("seat.confirmed"), eq("2"), any());

        // when
        int published = outboxService.relayPending(100);

        // then
        assertThat(published).isEqualTo(1);
        assertThat(ok.getPublishedAt()).isNotNull();
        assertThat(broken.getPublishedAt()).isNull();
        assertThat(broken.getAttempts()).isEqualTo(1);
    }

    // 원래 DTO 객체로 되돌려 보내야 Kafka 직렬화기가 기존과 같은 타입 헤더를 붙인다
    @Test
    @DisplayName("저장된 JSON을 원래 DTO로 되돌려 같은 토픽/키로 보낸다")
    void relayPending_sendsOriginalDto() {
        // given
        SeatConfirmedEvent payload = SeatConfirmedEvent.of(1L, 10L, 100L);
        when(repository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of(event("1", payload)));
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(any(), any(), any());

        // when
        outboxService.relayPending(100);

        // then
        verify(kafkaTemplate).send("seat.confirmed", "1", payload);
    }

    // 브로커가 응답하지 않아도 대기 시간 안에 끝나고, 응답 못 받은 건은 실패로 남아야 한다
    @Test
    @DisplayName("브로커가 응답하지 않으면 대기 시간이 지난 뒤 실패로 처리한다")
    void relayPending_brokerHangs_timesOut() {
        // given
        OutboxEvent hanging = event("1", SeatConfirmedEvent.of(1L, 10L, 100L));
        when(repository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of(hanging));
        doReturn(new CompletableFuture<>()).when(kafkaTemplate).send(any(), any(), any());

        // when
        long started = System.nanoTime();
        int published = outboxService.relayPending(100);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        // then
        assertThat(published).isZero();
        assertThat(hanging.getPublishedAt()).isNull();
        assertThat(hanging.getAttempts()).isEqualTo(1);
        assertThat(elapsedMillis).isLessThan(SEND_TIMEOUT_MILLIS + 1000);
    }

    // 되돌릴 수 없는 행이 있어도 예외로 묶음 전체가 실패하지 않고, 나머지는 발행되어야 한다
    @Test
    @DisplayName("타입을 알 수 없는 행은 실패로만 남기고 같은 묶음의 다른 이벤트는 발행한다")
    void relayPending_unknownType_doesNotBlockOthers() {
        // given
        OutboxEvent unknown = OutboxEvent.create("seat.confirmed", "1", "com.example.Missing", "{}");
        OutboxEvent ok = event("2", SeatConfirmedEvent.of(2L, 20L, 200L));
        when(repository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of(unknown, ok));
        doReturn(CompletableFuture.completedFuture(null)).when(kafkaTemplate).send(any(), any(), any());

        // when
        int published = outboxService.relayPending(100);

        // then
        assertThat(published).isEqualTo(1);
        assertThat(unknown.getPublishedAt()).isNull();
        assertThat(unknown.getAttempts()).isEqualTo(1);
        assertThat(ok.getPublishedAt()).isNotNull();
    }

    // 발행 대기 이벤트가 없으면 Kafka를 호출하지 않고 0을 돌려준다
    @Test
    @DisplayName("발행 대기 이벤트가 없으면 아무것도 보내지 않는다")
    void relayPending_nothingPending_returnsZero() {
        // given
        when(repository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of());

        // when & then
        assertThat(outboxService.relayPending(100)).isZero();
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    private OutboxEvent event(String key, Object payload) {
        return OutboxEvent.create("seat.confirmed", key, payload.getClass().getName(), jsonMapper.writeValueAsString(payload));
    }
}
