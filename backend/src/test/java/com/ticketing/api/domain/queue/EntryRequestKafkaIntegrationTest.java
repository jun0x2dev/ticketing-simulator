package com.ticketing.api.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.producer.EntryRequestProducer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

/**
 * EntryRequestProducer가 발행한 메시지가 실제로 Kafka 토픽을 거쳐
 * 역직렬화된 형태로 소비되는지 확인하는 통합 테스트.
 * 운영 코드(EventController)에 있던 수동 테스트용 엔드포인트를 대체한다.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"entry.requested"})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class EntryRequestKafkaIntegrationTest {

    @Autowired
    private EntryRequestProducer entryRequestProducer;

    @Autowired
    private TestEntryRequestListener testEntryRequestListener;

    // 발행한 이벤트가 토픽을 거쳐 별도 테스트 컨슈머 그룹에서 정상 역직렬화되는지 검증
    @Test
    @DisplayName("발행된 입장 요청 이벤트는 entry.requested 토픽을 통해 그대로 소비된다")
    void sendAndConsumeEntryRequestedEvent() throws InterruptedException {
        // given
        EntryRequestedEvent event = EntryRequestedEvent.of(1L);

        // when
        entryRequestProducer.send(event);
        boolean received = testEntryRequestListener.getLatch().await(5, TimeUnit.SECONDS);

        // then
        assertThat(received).isTrue();
        assertThat(testEntryRequestListener.getReceivedEvent().eventId()).isEqualTo(1L);
        assertThat(testEntryRequestListener.getReceivedEvent().requestId()).isEqualTo(event.requestId());
    }

    @TestConfiguration
    static class TestListenerConfig {

        @Bean
        TestEntryRequestListener testEntryRequestListener() {
            return new TestEntryRequestListener();
        }
    }

    /**
     * 애플리케이션의 실제 EntryRequestConsumer와는 별도의 컨슈머 그룹으로
     * 같은 토픽을 구독해서, 테스트에서 수신 여부를 직접 검증할 수 있게 하는 테스트 전용 리스너.
     */
    static class TestEntryRequestListener {

        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile EntryRequestedEvent receivedEvent;

        @KafkaListener(topics = "entry.requested", groupId = "test-entry-request-listener")
        void listen(EntryRequestedEvent event) {
            this.receivedEvent = event;
            latch.countDown();
        }

        CountDownLatch getLatch() {
            return latch;
        }

        EntryRequestedEvent getReceivedEvent() {
            return receivedEvent;
        }
    }
}
