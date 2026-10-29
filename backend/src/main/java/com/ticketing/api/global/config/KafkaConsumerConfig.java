package com.ticketing.api.global.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka Consumer 공통 에러 처리 설정. Consumer 처리가 실패하면 1초 간격으로 최대 3번 재시도하고,
 * 그래도 실패하면 "{원본 토픽}.DLT" 토픽으로 보내서 무한 재시도로 파티션이 막히는 것을 방지한다.
 * 역직렬화할 수 없는 메시지(ErrorHandlingDeserializer가 감지)는 재시도해도 같으므로 바로 DLT로 보낸다.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);
    private static final String DLT_SUFFIX = ".DLT";
    private static final long RETRY_INTERVAL_MILLIS = 1000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                 ProducerFactory<Object, Object> producerFactory) {
        // 역직렬화에 실패한 메시지는 원본 바이트로 DLT에 보내진다. JSON 직렬화기로 보내면 base64 문자열로 바뀌므로
        // 바이트 그대로 보관하는 템플릿을 따로 쓰고, 나머지 메시지는 기존 JSON 템플릿으로 보낸다.
        KafkaTemplate<Object, Object> bytesTemplate = new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class));
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, bytesTemplate);
        templates.put(Object.class, kafkaTemplate);

        // Spring Kafka 기본 DLT 접미사("-dlt")에 기대지 않고, KafkaTopicConfig에 선언한 ".DLT" 이름으로 명시한다.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(templates, (record, ex) -> {
            // DLT를 소비하다 실패한 메시지를 다시 "*.DLT.DLT"로 보내면 끝없이 토픽이 늘어나므로, 기록만 남기고 건너뛴다.
            if (record.topic().endsWith(DLT_SUFFIX)) {
                log.error("DLT 메시지 처리 실패, 건너뜀 - topic: {}, partition: {}, offset: {}",
                        record.topic(), record.partition(), record.offset(), ex);
                return null;
            }
            return new TopicPartition(record.topic() + DLT_SUFFIX, record.partition());
        });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MILLIS, MAX_RETRIES));
    }
}
