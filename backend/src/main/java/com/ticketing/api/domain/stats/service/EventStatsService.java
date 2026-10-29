package com.ticketing.api.domain.stats.service;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.stats.dto.EventStatsResponse;

/**
 * 회차별 예매 통계 집계/조회 비즈니스 로직 인터페이스. 모든 기록 메서드는 같은 메시지가 다시 와도 한 번만 센다.
 */
public interface EventStatsService {

    /**
     * 입장 허용 1건을 기록한다 (멱등 키: requestId).
     *
     * @param event 입장 허용 이벤트
     */
    void recordGranted(EntryGrantedEvent event);

    /**
     * 결제 완료 1건을 기록한다 (멱등 키: paymentId).
     *
     * @param event 좌석 확정 이벤트
     */
    void recordConfirmed(SeatConfirmedEvent event);

    /**
     * 좌석 해제 1건을 사유(결제 실패/시간 초과)별로 기록한다.
     *
     * @param event 좌석 해제 이벤트
     */
    void recordReleased(SeatReleasedEvent event);

    /**
     * 회차의 통계를 조회한다.
     *
     * @param eventId 회차(이벤트) ID
     * @return 통계 (기록이 없으면 모두 0)
     */
    EventStatsResponse getStats(Long eventId);
}
