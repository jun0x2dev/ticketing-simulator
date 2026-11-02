package com.ticketing.api.domain.stats.consumer;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.stats.service.EventStatsService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 통계 집계에 실패해 DLT로 빠진 입장 허용/결제 완료/좌석 해제 이벤트를 다시 집계한다.
 * 집계 실패의 흔한 원인은 Redis의 일시적 장애이므로, 잠시 기다린 뒤 같은 기록 메서드를 한 번 더 호출한다.
 * 기록은 메시지별 멱등 키로 한 번만 반영되므로, 이미 집계된 메시지(예: WebSocket 릴레이 실패로 DLT에 온 entry.granted)를 다시 넣어도 두 번 세지 않는다.
 * 여기서도 실패하면 공통 에러 핸들러가 재시도한 뒤 다시 DLT로 보내지 않고 error 로그를 남긴다(원본은 DLT에 남아 오프셋으로 찾을 수 있다).
 */
@Component
public class EventStatsDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventStatsDltConsumer.class);
    private static final String GROUP_ID = "ticketing-stats-dlt";

    private final EventStatsService eventStatsService;
    private final long retryDelayMillis;

    public EventStatsDltConsumer(EventStatsService eventStatsService,
                                 @Value("${stats.dlt.retry-delay-ms:5000}") long retryDelayMillis) {
        this.eventStatsService = eventStatsService;
        this.retryDelayMillis = retryDelayMillis;
    }

    /**
     * DLT로 빠진 입장 허용 이벤트를 다시 집계한다.
     *
     * @param record entry.granted.DLT 메시지
     */
    @KafkaListener(topics = KafkaTopicConfig.ENTRY_GRANTED_DLT, groupId = GROUP_ID,
            properties = EventStatsConsumer.TRUSTED_PACKAGES)
    public void onGranted(ConsumerRecord<String, EntryGrantedEvent> record) throws InterruptedException {
        retry(record, eventStatsService::recordGranted);
    }

    /**
     * DLT로 빠진 결제 완료 이벤트를 다시 집계한다.
     *
     * @param record seat.confirmed.DLT 메시지
     */
    @KafkaListener(topics = KafkaTopicConfig.SEAT_CONFIRMED_DLT, groupId = GROUP_ID,
            properties = EventStatsConsumer.TRUSTED_PACKAGES)
    public void onConfirmed(ConsumerRecord<String, SeatConfirmedEvent> record) throws InterruptedException {
        retry(record, eventStatsService::recordConfirmed);
    }

    /**
     * DLT로 빠진 좌석 해제 이벤트를 다시 집계한다.
     *
     * @param record seat.released.DLT 메시지
     */
    @KafkaListener(topics = KafkaTopicConfig.SEAT_RELEASED_DLT, groupId = GROUP_ID,
            properties = EventStatsConsumer.TRUSTED_PACKAGES)
    public void onReleased(ConsumerRecord<String, SeatReleasedEvent> record) throws InterruptedException {
        retry(record, eventStatsService::recordReleased);
    }

    // 바로 다시 시도하면 같은 장애로 또 실패하므로, 메시지 시각 기준으로 남은 시간만큼만 기다린다(밀려 있던 메시지는 기다리지 않는다).
    // 종료 중 대기가 끊기면 InterruptedException을 그대로 던진다. 정상 반환하면 집계하지 않은 메시지의 오프셋이 커밋되어 사라진다.
    private <T> void retry(ConsumerRecord<String, T> record, Consumer<T> recorder) throws InterruptedException {
        // 값이 없는 메시지(tombstone)는 집계할 대상이 없으므로 건너뛴다.
        if (record.value() == null) {
            log.error("통계 DLT 메시지 값 없음, 건너뜀 - topic: {}, partition: {}, offset: {}",
                    record.topic(), record.partition(), record.offset());
            return;
        }
        long waitMillis = record.timestamp() + retryDelayMillis - System.currentTimeMillis();
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
        recorder.accept(record.value());
        log.info("통계 DLT 재집계 - topic: {}, partition: {}, offset: {}", record.topic(), record.partition(), record.offset());
    }
}
