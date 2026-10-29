package com.ticketing.api.domain.queue.consumer;

import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.global.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * "entry.granted" 토픽을 구독해서 입장이 허용된 요청의 대기 화면에 WebSocket으로 알려주는 릴레이.
 * 메시지에는 토큰이 없으므로, 클라이언트는 알림을 받은 뒤 {@code GET /api/v1/events/{eventId}/entries/{requestId}}로 토큰을 가져간다.
 */
@Component
@RequiredArgsConstructor
public class EntryGrantedRelayConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 입장 허용 이벤트를 "/topic/entries/{requestId}"로 전달한다.
     * 인스턴스마다 자기 WebSocket 접속자에게 전달해야 하므로 인스턴스별 고유 그룹으로 구독하고,
     * 재시작 시 과거 메시지를 다시 흘리지 않도록 최신 오프셋부터 읽는다.
     *
     * @param event 입장 허용 이벤트
     */
    @KafkaListener(topics = KafkaTopicConfig.ENTRY_GRANTED, groupId = "ws-relay-${random.uuid}",
            properties = "auto.offset.reset=latest")
    public void relay(EntryGrantedEvent event) {
        messagingTemplate.convertAndSend("/topic/entries/" + event.requestId(), event);
    }
}
