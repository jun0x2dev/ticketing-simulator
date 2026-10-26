package com.ticketing.api.domain.queue.consumer;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * "entry.requested" 토픽을 소비하는 Consumer.
 * 현재는 수신 확인용 placeholder로 로그만 남긴다 — 입장 허용 토큰 발급, TPS 제한
 * 등 실제 대기열 처리 로직은 simulation/queue 기능 구현 단계에서 추가한다.
 */
@Component
public class EntryRequestConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntryRequestConsumer.class);

    /**
     * 입장 요청 이벤트를 수신해서 로그를 남긴다.
     *
     * @param event 수신한 입장 요청 이벤트
     */
    @KafkaListener(topics = "entry.requested", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(EntryRequestedEvent event) {
        log.info("입장 요청 수신 - requestId: {}, eventId: {}", event.requestId(), event.eventId());
    }
}
