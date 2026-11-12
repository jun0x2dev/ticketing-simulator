package com.ticketing.api.domain.stats.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.stats.dto.EventStatsResponse;
import com.ticketing.api.domain.stats.recorder.EventStatsRecorder;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

/**
 * EventStatsServiceImpl 단위 테스트. 이벤트별로 올바른 항목과 멱등 키로 기록하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class EventStatsServiceImplTest {

    @Mock
    private EventStatsRecorder recorder;

    @InjectMocks
    private EventStatsServiceImpl statsService;

    // 입장 허용은 requestId를 멱등 키로 granted 항목에 기록해야 한다
    @Test
    @DisplayName("입장 허용은 requestId 기준으로 granted에 기록한다")
    void recordGranted() {
        // given
        EntryGrantedEvent event = EntryGrantedEvent.from(EntryRequestedEvent.of(7L));

        // when
        statsService.recordGranted(event);

        // then
        verify(recorder).incrementOnce(7L, EventStatsResponse.GRANTED, "granted:" + event.requestId());
    }

    // 결제 완료는 paymentId를 멱등 키로 confirmed 항목에 기록해야 한다
    @Test
    @DisplayName("결제 완료는 paymentId 기준으로 confirmed에 기록한다")
    void recordConfirmed() {
        // when
        statsService.recordConfirmed(SeatConfirmedEvent.of(7L, 3L, 100L));

        // then
        verify(recorder).incrementOnce(7L, EventStatsResponse.CONFIRMED, "confirmed:100");
    }

    // 결제 실패 해제, 시간 초과 해제, 예매 취소는 서로 다른 항목에, 각자 식별 가능한 키로 기록해야 한다
    @Test
    @DisplayName("좌석 해제는 사유에 따라 paymentFailed/ttlExpired/cancelled로 나눠 기록한다")
    void recordReleased_byReason() {
        // given
        SeatReleasedEvent paymentFailed = SeatReleasedEvent.paymentFailed(7L, 3L, 101L);
        SeatReleasedEvent ttlExpired = SeatReleasedEvent.ttlExpired(7L, 4L);
        SeatReleasedEvent cancelled = SeatReleasedEvent.bookingCancelled(7L, 5L, 102L);

        // when
        statsService.recordReleased(paymentFailed);
        statsService.recordReleased(ttlExpired);
        statsService.recordReleased(cancelled);

        // then
        verify(recorder).incrementOnce(7L, EventStatsResponse.PAYMENT_FAILED, "released:payment:101");
        verify(recorder).incrementOnce(7L, EventStatsResponse.TTL_EXPIRED,
                "released:ttl:4:" + ttlExpired.occurredAt().toEpochMilli());
        verify(recorder).incrementOnce(7L, EventStatsResponse.CANCELLED, "released:cancel:102");
    }

    // 기록이 없는 항목은 0으로 채워 응답해야 한다
    @Test
    @DisplayName("기록이 없는 항목은 0으로 조회된다")
    void getStats_fillsMissingWithZero() {
        // given
        when(recorder.getAll(7L)).thenReturn(Map.of(EventStatsResponse.CONFIRMED, 2L));

        // when
        EventStatsResponse stats = statsService.getStats(7L);

        // then
        assertThat(stats.confirmed()).isEqualTo(2);
        assertThat(stats.granted()).isZero();
        assertThat(stats.ttlExpired()).isZero();
    }

    // Redis만 쓰는 통계 처리가 트랜잭션을 열면 조회/집계마다 DB 커넥션을 잡아 좌석 선점·결제와 커넥션을 다툰다
    @Test
    @DisplayName("통계 서비스 메서드는 트랜잭션 없이 실행되어 DB 커넥션을 잡지 않는다")
    void statsService_runsWithoutTransaction() throws NoSuchMethodException {
        // given
        AnnotationTransactionAttributeSource source = new AnnotationTransactionAttributeSource();

        // when & then
        assertThat(source.getTransactionAttribute(
                EventStatsServiceImpl.class.getMethod("getStats", Long.class), EventStatsServiceImpl.class)).isNull();
        assertThat(source.getTransactionAttribute(
                EventStatsServiceImpl.class.getMethod("recordGranted", EntryGrantedEvent.class), EventStatsServiceImpl.class)).isNull();
    }
}
