package com.ticketing.api.domain.payment.producer;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.global.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 좌석 확정/해제 이벤트를 "seat.confirmed", "seat.released" 토픽에 발행하는 Producer.
 * 파티션 키를 eventId로 둬서 같은 이벤트의 좌석 변경이 순서대로 처리되도록 한다.
 */
@Component
@RequiredArgsConstructor
public class SeatEventProducer {

    private static final Logger log = LoggerFactory.getLogger(SeatEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 좌석 확정 이벤트를 발행한다.
     *
     * @param event 발행할 이벤트
     */
    public void sendConfirmed(SeatConfirmedEvent event) {
        send(KafkaTopicConfig.SEAT_CONFIRMED, event.eventId(), event);
    }

    /**
     * 좌석 해제 이벤트를 발행한다.
     *
     * @param event 발행할 이벤트
     */
    public void sendReleased(SeatReleasedEvent event) {
        send(KafkaTopicConfig.SEAT_RELEASED, event.eventId(), event);
    }

    private void send(String topic, Long eventId, Object payload) {
        kafkaTemplate.send(topic, String.valueOf(eventId), payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("좌석 이벤트 발행 실패 - topic: {}, eventId: {}", topic, eventId, ex);
                    } else {
                        log.info("좌석 이벤트 발행 완료 - topic: {}, eventId: {}", topic, eventId);
                    }
                });
    }
}
