package com.ticketing.api.domain.queue.consumer;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 현재는 수신 확인용 placeholder. 입장 허용 토큰 발급, TPS 제한 등
 * 실제 대기열 처리 로직은 이후 simulation/queue 기능 구현 단계에서 추가한다.
 */
@Component
public class EntryRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntryRequestConsumer.class);

    @KafkaListener(topics = "entry.requested", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(EntryRequestedEvent event) {
        log.info("입장 요청 수신 - requestId: {}, eventId: {}", event.requestId(), event.eventId());
    }
}
