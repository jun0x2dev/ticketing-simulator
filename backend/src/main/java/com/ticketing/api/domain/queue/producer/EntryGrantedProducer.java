package com.ticketing.api.domain.queue.producer;

import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.global.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 입장 허용 이벤트를 "entry.granted" 토픽에 발행하는 Producer. 파티션 키는 eventId.
 */
@Component
@RequiredArgsConstructor
public class EntryGrantedProducer {

    private static final Logger log = LoggerFactory.getLogger(EntryGrantedProducer.class);

    private final KafkaTemplate<String, EntryGrantedEvent> kafkaTemplate;

    /**
     * 입장 허용 이벤트를 발행한다.
     *
     * @param event 발행할 이벤트
     */
    public void send(EntryGrantedEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.ENTRY_GRANTED, String.valueOf(event.eventId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("입장 허용 이벤트 발행 실패 - requestId: {}", event.requestId(), ex);
                    } else {
                        log.info("입장 허용 이벤트 발행 완료 - requestId: {}, eventId: {}", event.requestId(), event.eventId());
                    }
                });
    }
}
