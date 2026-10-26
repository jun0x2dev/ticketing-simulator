package com.ticketing.api.global.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka Consumer 공통 에러 처리 설정. Consumer 처리가 실패하면 1초 간격으로 최대 3번 재시도하고,
 * 그래도 실패하면 "{원본 토픽}.DLT" 토픽으로 보내서 무한 재시도로 파티션이 막히는 것을 방지한다.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MILLIS = 1000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        // Spring Kafka 기본 DLT 접미사("-dlt")에 기대지 않고, KafkaTopicConfig에 선언한 ".DLT" 이름으로 명시한다.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES));
    }
}
