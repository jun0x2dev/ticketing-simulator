package com.ticketing.api.domain.queue.consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.service.EntryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EntryRequestDltConsumer 단위 테스트. DLT가 마지막 처리 단계이므로 예외를 밖으로 던지지 않는지 검증한다.
 */
class EntryRequestDltConsumerTest {

    private final EntryService entryService = mock(EntryService.class);
    private final EntryRequestDltConsumer consumer = new EntryRequestDltConsumer(entryService, 0);

    // DLT 메시지는 서비스의 DLT 처리로 넘겨야 한다
    @Test
    @DisplayName("DLT 메시지를 서비스의 재처리 로직으로 넘긴다")
    void consume_delegatesToService() throws InterruptedException {
        // given
        EntryRequestedEvent event = EntryRequestedEvent.of(1L);

        // when
        consumer.consume(new ConsumerRecord<>("entry.requested.DLT", 0, 0L, "1", event));

        // then
        verify(entryService).handleDeadLetter(event);
    }

    // 재처리 중 예외가 나도 다시 DLT로 보내지 않도록 예외를 삼키고 로그만 남겨야 한다
    @Test
    @DisplayName("재처리 중 예외가 나도 밖으로 던지지 않는다")
    void consume_swallowsException() {
        // given
        EntryRequestedEvent event = EntryRequestedEvent.of(1L);
        doThrow(new IllegalStateException("Redis 장애")).when(entryService).handleDeadLetter(event);

        // when & then
        assertThatCode(() -> consumer.consume(new ConsumerRecord<>("entry.requested.DLT", 0, 0L, "1", event)))
                .doesNotThrowAnyException();
    }

    // 종료 중 대기가 끊기면 처리하지 않은 메시지가 커밋되지 않도록 예외로 끝나야 한다
    @Test
    @DisplayName("재처리 대기 중 인터럽트되면 처리하지 않고 InterruptedException을 던진다")
    void consume_interruptedWhileWaiting_throws() {
        // given: 도착 시각과 관계없이 대기하도록 지연을 충분히 길게 두고, 대기 전에 인터럽트를 건다
        EntryRequestDltConsumer waitingConsumer = new EntryRequestDltConsumer(entryService, Long.MAX_VALUE / 2);
        Thread.currentThread().interrupt();

        // when & then
        assertThatThrownBy(() -> waitingConsumer.consume(
                new ConsumerRecord<>("entry.requested.DLT", 0, 0L, "1", EntryRequestedEvent.of(1L))))
                .isInstanceOf(InterruptedException.class);
        verify(entryService, never()).handleDeadLetter(any());
    }
}
