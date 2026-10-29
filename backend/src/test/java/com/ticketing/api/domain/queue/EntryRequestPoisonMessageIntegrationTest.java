package com.ticketing.api.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.producer.EntryRequestProducer;
import com.ticketing.api.domain.queue.service.EntryService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 역직렬화할 수 없는 메시지(poison message)가 들어와도 Consumer가 멈추지 않는지 검증한다.
 * ErrorHandlingDeserializer가 없으면 같은 메시지를 끝없이 다시 읽어 뒤의 정상 메시지가 영원히 처리되지 않는다.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"entry.requested", "entry.requested.DLT"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // DLT 재처리 대기 없이 바로 소비해, DLT Consumer가 깨진 메시지를 만났을 때의 동작도 함께 확인한다
        "queue.dlt.redrive-delay-ms=0"
})
class EntryRequestPoisonMessageIntegrationTest {

    private static final String POISON = "not-json";

    // Queue Worker/DLT Consumer는 실제로 동작하고, 그 뒤의 Redis/토큰 처리만 대체한다
    @MockitoBean
    private EntryService entryService;

    @Autowired
    private EntryRequestProducer entryRequestProducer;

    @Autowired
    private EmbeddedKafkaBroker broker;

    // 깨진 메시지는 원본 그대로 DLT로 옮겨지고, 바로 뒤의 정상 메시지는 처리되어야 한다
    @Test
    @DisplayName("역직렬화할 수 없는 메시지는 DLT로 옮기고 다음 메시지를 계속 처리한다")
    void poisonMessage_movedToDlt_nextMessageProcessed() {
        // given
        sendRaw(KafkaTopicConfig.ENTRY_REQUESTED, POISON);
        EntryRequestedEvent valid = EntryRequestedEvent.of(1L);

        // when
        entryRequestProducer.send(valid);

        // then
        verify(entryService, timeout(20000)).grantEntry(argThat(e -> e.requestId().equals(valid.requestId())));
        assertThat(readDltValues()).containsExactly(POISON);
        // DLT Consumer도 깨진 메시지를 처리하려다 멈추거나 재처리를 시도하지 않아야 한다
        verify(entryService, after(2000).never()).handleDeadLetter(any());
    }

    private void sendRaw(String topic, String value) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, "1", value));
            producer.flush();
        }
    }

    // DLT에 쌓인 메시지를 바이트 그대로 읽어 원본이 보존되었는지 확인한다
    private List<String> readDltValues() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "poison-dlt-check",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(KafkaTopicConfig.ENTRY_REQUESTED_DLT));
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline) {
                List<String> values = new ArrayList<>();
                for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(500))) {
                    values.add(new String(record.value()));
                }
                if (!values.isEmpty()) {
                    return values;
                }
            }
            return List.of();
        }
    }
}
