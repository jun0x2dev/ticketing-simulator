package com.ticketing.api.domain.queue.producer;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.global.config.KafkaTopicConfig;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 입장 요청 이벤트를 "entry.requested" 토픽에 발행하는 Producer.
 * 파티션 키를 eventId로 둬서, 같은 이벤트(공연)에 대한 요청은 같은 파티션에서
 * 순서대로 처리되도록 보장한다.
 */
@Component
@RequiredArgsConstructor
public class EntryRequestProducer {

    private static final Logger log = LoggerFactory.getLogger(EntryRequestProducer.class);

    private final KafkaTemplate<String, EntryRequestedEvent> kafkaTemplate;

    /**
     * 입장 요청 이벤트를 발행한다. 접수 응답을 늦추지 않도록 브로커 응답을 기다리지 않고,
     * 결과는 반환한 future로 알린다. 실패 처리(줄에서 빼고 실패 표시)는 호출한 쪽이 맡는다.
     *
     * @param event 발행할 입장 요청 이벤트
     * @return 브로커가 수신을 확인하면 정상 완료되고, 발행에 실패하면 예외로 완료되는 future
     */
    public CompletableFuture<?> send(EntryRequestedEvent event) {
        String key = String.valueOf(event.eventId());
        return kafkaTemplate.send(KafkaTopicConfig.ENTRY_REQUESTED, key, event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("입장 요청 이벤트 발행 완료 - requestId: {}, eventId: {}", event.requestId(), event.eventId());
                    }
                });
    }
}
