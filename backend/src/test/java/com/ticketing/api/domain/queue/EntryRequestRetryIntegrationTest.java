package com.ticketing.api.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.producer.EntryRequestProducer;
import com.ticketing.api.domain.queue.service.EntryService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 입장 요청 처리가 계속 실패할 때 무한 재시도하지 않고, 정해진 횟수(최초 1회 + 재시도 3회) 후
 * entry.requested.DLT 토픽으로 넘어가는지 검증하는 통합 테스트.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"entry.requested"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "queue.worker.tps=1000",
        "queue.dlt.redrive-delay-ms=0"
})
class EntryRequestRetryIntegrationTest {

    @MockitoBean
    private EntryService entryService;

    @Autowired
    private EntryRequestProducer entryRequestProducer;

    @Autowired
    private DltListener dltListener;

    // 항상 실패하는 Consumer는 4번 시도된 뒤 DLT로 넘어가야 한다
    @Test
    @DisplayName("처리가 계속 실패하면 재시도 후 DLT 토픽으로 전달된다")
    void consume_alwaysFails_movesToDlt() throws InterruptedException {
        // given
        doThrow(new IllegalStateException("처리 실패")).when(entryService).grantEntry(any(EntryRequestedEvent.class));
        EntryRequestedEvent event = EntryRequestedEvent.of(1L);

        // when
        entryRequestProducer.send(event);
        boolean arrived = dltListener.latch.await(20, TimeUnit.SECONDS);

        // then
        assertThat(arrived).isTrue();
        assertThat(dltListener.received.requestId()).isEqualTo(event.requestId());
        verify(entryService, timeout(1000).times(4)).grantEntry(any(EntryRequestedEvent.class));
        // DLT Consumer가 같은 메시지를 받아 재처리(재투입 또는 최종 실패)를 맡아야 한다
        verify(entryService, timeout(10000)).handleDeadLetter(any(EntryRequestedEvent.class));
    }

    @TestConfiguration
    static class DltListenerConfig {

        @Bean
        DltListener dltListener() {
            return new DltListener();
        }
    }

    /** DLT 토픽을 별도 그룹으로 구독해서 도착 여부를 확인하는 테스트 전용 리스너. */
    static class DltListener {

        private final CountDownLatch latch = new CountDownLatch(1);
        private volatile EntryRequestedEvent received;

        @KafkaListener(topics = "entry.requested.DLT", groupId = "test-dlt-listener")
        void listen(EntryRequestedEvent event) {
            this.received = event;
            latch.countDown();
        }
    }
}
