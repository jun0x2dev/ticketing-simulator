package com.ticketing.api.domain.stats;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.stats.service.EventStatsService;
import com.ticketing.api.global.config.KafkaTopicConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 통계 집계가 재시도 끝에 실패해 DLT로 빠진 메시지를, 통계 DLT Consumer가 다시 집계하는지 검증한다.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {
        "entry.granted", "seat.confirmed", "seat.released",
        "entry.granted.DLT", "seat.confirmed.DLT", "seat.released.DLT"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // 재집계 대기 없이 바로 소비한다
        "stats.dlt.retry-delay-ms=0"
})
class EventStatsDltIntegrationTest {

    // 첫 처리 1회 + 공통 에러 핸들러 재시도 3회
    private static final int PRIMARY_ATTEMPTS = 4;

    @MockitoBean
    private EventStatsService eventStatsService;

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    // Redis 장애 등으로 통계 Consumer가 재시도까지 모두 실패해도, 장애가 풀리면 DLT 재처리로 집계되어야 한다
    @Test
    @DisplayName("재시도까지 실패한 결제 완료 집계는 DLT에서 다시 집계된다")
    void failedRecord_recordedAgainFromDlt() {
        // given
        SeatConfirmedEvent event = SeatConfirmedEvent.of(401L, 1L, 4001L);
        IllegalStateException redisDown = new IllegalStateException("Redis 장애");
        doThrow(redisDown, redisDown, redisDown, redisDown).doNothing().when(eventStatsService).recordConfirmed(event);

        // when
        kafkaTemplate.send(KafkaTopicConfig.SEAT_CONFIRMED, "401", event);

        // then
        verify(eventStatsService, timeout(30000).times(PRIMARY_ATTEMPTS + 1)).recordConfirmed(event);
    }

    // 값이 없는 메시지는 건너뛰고, 뒤의 입장 허용/좌석 해제 DLT 메시지는 각각의 기록 메서드로 다시 집계해야 한다
    @Test
    @DisplayName("값 없는 DLT 메시지는 건너뛰고 입장 허용/좌석 해제 DLT 메시지를 다시 집계한다")
    void dltMessages_recordedAndTombstoneSkipped() {
        // given
        EntryGrantedEvent granted = EntryGrantedEvent.from(EntryRequestedEvent.of(402L));
        SeatReleasedEvent released = SeatReleasedEvent.ttlExpired(402L, 2L);

        // when
        kafkaTemplate.send(KafkaTopicConfig.ENTRY_GRANTED_DLT, "402", null);
        kafkaTemplate.send(KafkaTopicConfig.ENTRY_GRANTED_DLT, "402", granted);
        kafkaTemplate.send(KafkaTopicConfig.SEAT_RELEASED_DLT, "402", released);

        // then
        verify(eventStatsService, timeout(20000)).recordGranted(argThat(e -> e.requestId().equals(granted.requestId())));
        verify(eventStatsService, timeout(20000)).recordReleased(released);
    }
}
