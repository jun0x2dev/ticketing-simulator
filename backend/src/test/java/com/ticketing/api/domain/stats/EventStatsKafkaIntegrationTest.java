package com.ticketing.api.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ticketing.api.domain.outbox.entity.OutboxEvent;
import com.ticketing.api.domain.outbox.repository.OutboxEventRepository;
import com.ticketing.api.domain.outbox.service.OutboxService;
import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.producer.EntryGrantedProducer;
import com.ticketing.api.domain.stats.service.EventStatsService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 실제 Producer와 outbox 발행기가 보낸 입장 허용/결제 이벤트가 통계 Consumer까지 역직렬화되어 전달되는지 검증한다.
 * 결제 이벤트는 outbox에 JSON으로 저장했다가 원래 DTO로 되돌려 보내므로, 타입 헤더가 기존과 같아 Consumer가 그대로 받는지가 핵심이다.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"entry.granted", "seat.confirmed", "seat.released"})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class EventStatsKafkaIntegrationTest {

    @MockitoBean
    private EventStatsService eventStatsService;

    @Autowired
    private EntryGrantedProducer entryGrantedProducer;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        // 같은 인메모리 DB를 쓰는 다른 테스트가 남긴 outbox 행이 함께 발행되지 않도록 비운다
        outboxEventRepository.deleteAll();
    }

    // 세 토픽의 이벤트가 각각 통계 서비스의 해당 기록 메서드로 전달되고, outbox 행은 발행 완료로 표시되어야 한다
    @Test
    @DisplayName("입장 허용/결제 완료/좌석 해제 이벤트가 outbox 발행을 거쳐 통계 Consumer로 전달된다")
    void events_reachStatsConsumer() {
        // given
        SeatConfirmedEvent confirmed = SeatConfirmedEvent.of(301L, 1L, 3001L);
        SeatReleasedEvent released = SeatReleasedEvent.ttlExpired(301L, 2L);
        transactionTemplate.executeWithoutResult(status -> {
            outboxService.append(KafkaTopicConfig.SEAT_CONFIRMED, "301", confirmed);
            outboxService.append(KafkaTopicConfig.SEAT_RELEASED, "301", released);
        });

        // when
        entryGrantedProducer.send(EntryGrantedEvent.from(EntryRequestedEvent.of(301L)));
        int published = outboxService.relayPending(100);

        // then
        assertThat(published).isEqualTo(2);
        assertThat(outboxEventRepository.findAll()).allSatisfy(event -> assertThat(event.getPublishedAt()).isNotNull())
                .extracting(OutboxEvent::getAttempts).containsOnly(0);
        verify(eventStatsService, timeout(15000)).recordGranted(argThat(e -> e.eventId().equals(301L)));
        // occurredAt까지 원본과 같아야 통계의 멱등 키(released:ttl:{seatId}:{occurredAt})가 재발행에도 그대로 유지된다
        verify(eventStatsService, timeout(15000)).recordConfirmed(confirmed);
        verify(eventStatsService, timeout(15000)).recordReleased(released);
    }
}
