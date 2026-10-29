package com.ticketing.api.domain.stats;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.payment.producer.SeatEventProducer;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.producer.EntryGrantedProducer;
import com.ticketing.api.domain.stats.service.EventStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 실제 Producer가 발행한 입장 허용/결제 이벤트가 통계 Consumer까지 역직렬화되어 전달되는지 검증한다.
 * 결제 이벤트 DTO는 기본 역직렬화 허용 패키지 밖이라, 리스너 단위 설정이 동작하는지 확인하는 것이 핵심이다.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"entry.granted", "seat.confirmed", "seat.released"})
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class EventStatsKafkaIntegrationTest {

    @MockitoBean
    private EventStatsService eventStatsService;

    @Autowired
    private SeatEventProducer seatEventProducer;

    @Autowired
    private EntryGrantedProducer entryGrantedProducer;

    // 세 토픽의 이벤트가 각각 통계 서비스의 해당 기록 메서드로 전달되어야 한다
    @Test
    @DisplayName("입장 허용/결제 완료/좌석 해제 이벤트가 통계 Consumer로 전달된다")
    void events_reachStatsConsumer() {
        // when
        entryGrantedProducer.send(EntryGrantedEvent.from(EntryRequestedEvent.of(301L)));
        seatEventProducer.sendConfirmed(SeatConfirmedEvent.of(301L, 1L, 3001L));
        seatEventProducer.sendReleased(SeatReleasedEvent.ttlExpired(301L, 2L));

        // then
        verify(eventStatsService, timeout(15000)).recordGranted(argThat(e -> e.eventId().equals(301L)));
        verify(eventStatsService, timeout(15000)).recordConfirmed(argThat(e -> e.paymentId().equals(3001L)));
        verify(eventStatsService, timeout(15000)).recordReleased(argThat(e -> e.seatId().equals(2L)));
    }
}
