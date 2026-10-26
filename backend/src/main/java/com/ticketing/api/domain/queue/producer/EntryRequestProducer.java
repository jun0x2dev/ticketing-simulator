package com.ticketing.api.domain.queue.producer;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.global.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntryRequestProducer {

    private static final Logger log = LoggerFactory.getLogger(EntryRequestProducer.class);

    private final KafkaTemplate<String, EntryRequestedEvent> kafkaTemplate;

    public void send(EntryRequestedEvent event) {
        String key = String.valueOf(event.eventId());
        kafkaTemplate.send(KafkaTopicConfig.ENTRY_REQUESTED, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("입장 요청 이벤트 발행 실패 - requestId: {}", event.requestId(), ex);
                    } else {
                        log.info("입장 요청 이벤트 발행 완료 - requestId: {}, eventId: {}", event.requestId(), event.eventId());
                    }
                });
    }
}
