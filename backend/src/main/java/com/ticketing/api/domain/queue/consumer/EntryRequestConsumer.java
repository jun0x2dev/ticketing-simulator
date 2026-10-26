package com.ticketing.api.domain.queue.consumer;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.service.EntryService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * "entry.requested" 토픽을 일정 처리량(TPS)으로만 소비해서 입장을 허용하는 Queue Worker.
 * 처리 실패 시 재시도/DLQ 전략은 {@code KafkaConsumerConfig}의 에러 핸들러가 담당한다.
 */
@Component
public class EntryRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntryRequestConsumer.class);

    private final EntryService entryService;
    private final long intervalMillis;

    public EntryRequestConsumer(EntryService entryService, @Value("${queue.worker.tps:100}") int tps) {
        this.entryService = entryService;
        this.intervalMillis = 1000L / tps;
    }

    /**
     * 입장 요청 이벤트를 하나 처리하고, 설정된 TPS를 넘지 않도록 다음 소비 전에 대기한다.
     * 리스너 스레드를 직접 재워서 속도를 조절하므로, 소비되지 못한 요청은 Kafka에 그대로 쌓여 대기열 역할을 한다.
     *
     * @param event 수신한 입장 요청 이벤트
     */
    // ponytail: 리스너 스레드 1개 기준의 sleep 방식이라 파티션별 동시성을 늘리면 TPS도 비례해서 늘어난다. 정밀 제어가 필요하면 토큰 버킷으로 교체.
    @KafkaListener(topics = KafkaTopicConfig.ENTRY_REQUESTED, groupId = "${spring.kafka.consumer.group-id}")
    public void consume(EntryRequestedEvent event) {
        log.info("입장 요청 수신 - requestId: {}, eventId: {}", event.requestId(), event.eventId());
        entryService.grantEntry(event);
        try {
            Thread.sleep(intervalMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
