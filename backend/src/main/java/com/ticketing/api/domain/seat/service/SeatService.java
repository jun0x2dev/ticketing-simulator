package com.ticketing.api.domain.seat.service;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.seat.dto.SeatResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 좌석 초기화, 조회, 선점/해제/확정 비즈니스 로직 인터페이스.
 */
public interface SeatService {

    /**
     * 이벤트에 속한 좌석을 1번부터 seatCount번까지 생성한다.
     *
     * @param event     좌석이 속할 이벤트
     * @param seatCount 생성할 좌석 수
     */
    void initializeSeats(Event event, int seatCount);

    /**
     * 이벤트에 속한 모든 좌석을 조회한다.
     *
     * @param eventId 이벤트 ID
     * @return 좌석 목록
     */
    List<SeatResponse> getSeats(Long eventId);

    /**
     * 좌석을 선점한다. Redis 분산 락으로 동시 요청 중 하나만 성공하도록 보장한다.
     *
     * @param seatId 선점할 좌석 ID
     * @return 선점된 좌석 정보
     */
    SeatResponse holdSeat(Long seatId);

    /**
     * 선점된 좌석을 다시 선택 가능 상태로 되돌린다 (결제 실패/TTL 만료 시 사용).
     *
     * @param seatId 해제할 좌석 ID
     */
    void releaseSeat(Long seatId);

    /**
     * 선점 후 결제 제한시간이 지난 HELD 좌석을 AVAILABLE로 복구한다.
     * Redis 락은 TTL로 자동 만료되지만 DB 상태는 그대로이므로, 주기적으로 호출해 둘을 일치시킨다.
     *
     * @param heldBefore 이 시각 이전에 선점된 좌석을 복구 대상으로 본다
     * @return 복구된 좌석 수
     */
    int releaseExpiredSeats(LocalDateTime heldBefore);
}
