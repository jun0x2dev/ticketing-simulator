package com.ticketing.api.domain.queue.consumer;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.service.EntryService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * "entry.requested.DLT" 토픽을 소비해서, 재시도 끝에 실패한 입장 요청을 재투입하거나 최종 실패로 처리한다.
 * 이 Consumer가 마지막 처리 단계이므로 여기서 난 예외는 다시 DLT로 보내지 않고 에러 로그로 남긴다.
 */
@Component
public class EntryRequestDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(EntryRequestDltConsumer.class);

    private final EntryService entryService;
    private final long redriveDelayMillis;

    public EntryRequestDltConsumer(EntryService entryService,
                                   @Value("${queue.dlt.redrive-delay-ms:5000}") long redriveDelayMillis) {
        this.entryService = entryService;
        this.redriveDelayMillis = redriveDelayMillis;
    }

    /**
     * DLT에 도착한 지 일정 시간이 지난 뒤 처리한다. 원인이 일시적 장애라면 바로 다시 보내도 같은 이유로 실패하므로,
     * 메시지 도착 시각 기준으로 남은 시간만큼만 기다린다(밀려 있던 메시지는 기다리지 않는다).
     *
     * @param record DLT 메시지
     */
    @KafkaListener(topics = KafkaTopicConfig.ENTRY_REQUESTED_DLT, groupId = "${spring.kafka.consumer.group-id}-dlt")
    public void consume(ConsumerRecord<String, EntryRequestedEvent> record) {
        // 값이 없는 메시지(tombstone)는 처리할 요청이 없으므로 건너뛴다. 아래 로그에서 requestId를 꺼내다 실패하지 않게 먼저 거른다.
        if (record.value() == null) {
            log.error("DLT 입장 요청 값 없음, 건너뜀 - partition: {}, offset: {}", record.partition(), record.offset());
            return;
        }
        long waitMillis = record.timestamp() + redriveDelayMillis - System.currentTimeMillis();
        try {
            if (waitMillis > 0) {
                Thread.sleep(waitMillis);
            }
            entryService.handleDeadLetter(record.value());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("DLT 입장 요청 처리 실패 - requestId: {}", record.value().requestId(), e);
        }
    }
}
