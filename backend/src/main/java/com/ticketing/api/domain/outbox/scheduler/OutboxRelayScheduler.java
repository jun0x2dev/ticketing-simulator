package com.ticketing.api.domain.outbox.scheduler;

import com.ticketing.api.domain.outbox.service.OutboxService;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * outbox에 쌓인 이벤트를 주기적으로 Kafka로 발행하고, 발행이 끝난 오래된 행을 정리하는 스케줄러.
 * 테스트처럼 발행 시점을 직접 제어해야 할 때는 outbox.relay.enabled=false로 끌 수 있다.
 */
@Component
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {

    // 한 트랜잭션에서 잠그고 발행할 이벤트 수. 잠금 시간과 한 번의 대기 시간을 작게 유지한다.
    static final int RELAY_BATCH_SIZE = 100;
    // 한 트랜잭션에서 지울 발행 완료 이벤트 수.
    static final int PURGE_BATCH_SIZE = 1000;

    private final OutboxService outboxService;
    private final Duration retention;

    public OutboxRelayScheduler(OutboxService outboxService,
                                @Value("${outbox.retention-hours:24}") long retentionHours) {
        this.outboxService = outboxService;
        this.retention = Duration.ofHours(retentionHours);
    }

    /**
     * 이전 실행이 끝난 뒤 일정 간격(기본 1초)으로 발행 대기 이벤트를 발행한다.
     * 한 묶음을 모두 발행했다면 밀린 이벤트가 더 있을 수 있으므로 이어서 발행한다. 실패가 섞이면 멈추고 다음 주기를 기다려,
     * 브로커 장애 중에 실패만 반복하며 스케줄러 스레드를 붙잡지 않게 한다.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void relay() {
        int published;
        do {
            published = outboxService.relayPending(RELAY_BATCH_SIZE);
        } while (published == RELAY_BATCH_SIZE);
    }

    /**
     * 매시 30분에 보관 기간(기본 24시간)이 지난 발행 완료 이벤트를 묶음 단위로 모두 지운다.
     * 발행 대기 이벤트는 기간과 관계없이 지우지 않는다.
     */
    @Scheduled(cron = "${outbox.purge-cron:0 30 * * * *}")
    public void purgePublished() {
        LocalDateTime publishedBefore = LocalDateTime.now().minus(retention);
        int deleted;
        do {
            deleted = outboxService.deletePublishedBefore(publishedBefore, PURGE_BATCH_SIZE);
        } while (deleted == PURGE_BATCH_SIZE);
    }
}
