package com.ticketing.api.domain.outbox.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.outbox.service.OutboxService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OutboxRelayScheduler 단위 테스트. 밀린 이벤트는 이어서 발행하되, 실패가 섞이면 반복을 멈추는지 검증한다.
 */
class OutboxRelaySchedulerTest {

    private final OutboxService outboxService = mock(OutboxService.class);
    private final OutboxRelayScheduler scheduler = new OutboxRelayScheduler(outboxService, 24);

    // 묶음을 모두 발행했다면 밀린 이벤트가 더 있을 수 있으므로 덜 찬 묶음이 나올 때까지 이어서 발행해야 한다
    @Test
    @DisplayName("묶음을 모두 발행하면 다음 묶음을 이어서 발행한다")
    void relay_repeatsWhileBatchFullyPublished() {
        // given
        int batch = OutboxRelayScheduler.RELAY_BATCH_SIZE;
        when(outboxService.relayPending(batch)).thenReturn(batch, batch, 7);

        // when
        scheduler.relay();

        // then
        verify(outboxService, times(3)).relayPending(batch);
    }

    // 브로커 장애로 일부가 실패하면 같은 주기 안에서 반복하지 않고 다음 주기를 기다려야 한다
    @Test
    @DisplayName("발행 실패가 섞이면 반복하지 않고 다음 주기를 기다린다")
    void relay_stopsWhenSomeFailed() {
        // given
        int batch = OutboxRelayScheduler.RELAY_BATCH_SIZE;
        when(outboxService.relayPending(batch)).thenReturn(batch - 1);

        // when
        scheduler.relay();

        // then
        verify(outboxService, times(1)).relayPending(batch);
    }

    // 지울 대상이 묶음 크기만큼 남아 있으면 이어서 지우고, 덜 차면 멈춰야 한다
    @Test
    @DisplayName("발행 완료 이벤트를 묶음 단위로 남김없이 정리한다")
    void purge_repeatsWhileBatchIsFull() {
        // given
        int batch = OutboxRelayScheduler.PURGE_BATCH_SIZE;
        when(outboxService.deletePublishedBefore(any(LocalDateTime.class), eq(batch))).thenReturn(batch, 0);

        // when
        scheduler.purgePublished();

        // then
        verify(outboxService, times(2)).deletePublishedBefore(any(LocalDateTime.class), eq(batch));
    }
}
