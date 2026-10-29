package com.ticketing.api.domain.event.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.event.service.EventService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EventRunCleanupScheduler 단위 테스트. 삭제 대상이 남아 있는 동안 묶음 단위로 반복하는지 검증한다.
 */
class EventRunCleanupSchedulerTest {

    private final EventService eventService = mock(EventService.class);
    private final EventRunCleanupScheduler scheduler = new EventRunCleanupScheduler(eventService, 24);

    // 묶음이 가득 차면 다음 묶음을 이어서 지우고, 덜 차면 멈춰야 한다
    @Test
    @DisplayName("삭제한 회차 수가 묶음 크기와 같으면 다음 묶음을 이어서 지운다")
    void cleanUp_repeatsWhileBatchIsFull() {
        // given
        when(eventService.deleteRunsOpenedBefore(any(LocalDateTime.class), eq(EventRunCleanupScheduler.BATCH_SIZE)))
                .thenReturn(EventRunCleanupScheduler.BATCH_SIZE, EventRunCleanupScheduler.BATCH_SIZE, 3);

        // when
        scheduler.cleanUpExpiredRuns();

        // then
        verify(eventService, times(3)).deleteRunsOpenedBefore(any(LocalDateTime.class), eq(EventRunCleanupScheduler.BATCH_SIZE));
    }

    // 지울 대상이 없으면 한 번만 확인하고 끝나야 한다
    @Test
    @DisplayName("지울 회차가 없으면 한 번만 확인하고 끝난다")
    void cleanUp_stopsWhenNothingToDelete() {
        // given
        when(eventService.deleteRunsOpenedBefore(any(LocalDateTime.class), eq(EventRunCleanupScheduler.BATCH_SIZE))).thenReturn(0);

        // when
        scheduler.cleanUpExpiredRuns();

        // then
        verify(eventService, times(1)).deleteRunsOpenedBefore(any(LocalDateTime.class), eq(EventRunCleanupScheduler.BATCH_SIZE));
    }
}
