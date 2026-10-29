package com.ticketing.api.domain.event.scheduler;

import com.ticketing.api.domain.event.service.EventService;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 오래된 회차(Event)와 좌석을 주기적으로 삭제하는 스케줄러.
 * 사용자가 공연을 고를 때마다 회차가 생기므로, 정리하지 않으면 회차와 좌석 행(회차당 최대 500행)이 계속 쌓인다.
 */
@Component
public class EventRunCleanupScheduler {

    // 한 트랜잭션에서 지울 회차 수. 잠금 시간과 DELETE 크기를 작게 유지하기 위해 나눠서 지운다.
    static final int BATCH_SIZE = 100;

    private final EventService eventService;
    private final Duration retention;

    public EventRunCleanupScheduler(EventService eventService,
                                    @Value("${event.run-retention-hours:24}") long retentionHours) {
        this.eventService = eventService;
        this.retention = Duration.ofHours(retentionHours);
    }

    /**
     * 매시 정각에 보관 기간(기본 24시간)이 지난 회차를 묶음 단위로 모두 지운다.
     * 예매 한 번은 입장 토큰(10분) + 결제 제한시간(5분) 안에 끝나므로, 진행 중인 회차가 지워질 일은 없다.
     */
    @Scheduled(cron = "${event.run-cleanup-cron:0 0 * * * *}")
    public void cleanUpExpiredRuns() {
        LocalDateTime openedBefore = LocalDateTime.now().minus(retention);
        int deleted;
        // 묶음이 가득 찼다면 남은 대상이 더 있을 수 있으므로, 덜 찬 묶음이 나올 때까지 이어서 지운다.
        do {
            deleted = eventService.deleteRunsOpenedBefore(openedBefore, BATCH_SIZE);
        } while (deleted == BATCH_SIZE);
    }
}
