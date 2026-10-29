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
     * 좌석 1석을 조회한다.
     *
     * @param seatId 좌석 ID
     * @return 좌석 정보 (없으면 SEAT_NOT_FOUND 예외)
     */
    SeatResponse getSeat(Long seatId);

    /**
     * 요청자가 선점 중인 좌석을 조회한다. 결제 게이트웨이를 호출하기 전에 남의 좌석이나 만료된 좌석을 걸러낸다.
     *
     * @param seatId     좌석 ID
     * @param entryToken 요청자의 입장 토큰
     * @return 좌석 정보 (HELD가 아니면 SEAT_NOT_HELD, 선점자가 다르면 SEAT_HOLDER_MISMATCH 예외)
     */
    SeatResponse getHeldSeat(Long seatId, String entryToken);

    /**
     * 결제 완료로 선점된 좌석을 확정(CONFIRMED)한다. 행 잠금 후 선점자를 다시 확인한다.
     *
     * @param seatId     확정할 좌석 ID
     * @param entryToken 선점한 사용자의 입장 토큰
     */
    void confirmSeat(Long seatId, String entryToken);

    /**
     * 좌석을 선점한다. 입장 토큰을 먼저 검증하고, Redis 분산 락으로 동시 요청 중 하나만 성공하도록 보장한다.
     * 토큰 하나로는 동시에 1석만 선점할 수 있다.
     *
     * @param seatId     선점할 좌석 ID
     * @param entryToken 대기열을 통과해 발급받은 입장 토큰 (좌석이 속한 이벤트의 토큰이어야 한다)
     * @return 선점된 좌석 정보
     */
    SeatResponse holdSeat(Long seatId, String entryToken);

    /**
     * 선점자가 좌석을 다시 선택 가능 상태로 되돌린다 (결제 실패, 사용자의 선점 취소 시 사용).
     *
     * @param seatId     해제할 좌석 ID
     * @param entryToken 선점한 사용자의 입장 토큰
     */
    void releaseSeat(Long seatId, String entryToken);

    /**
     * 선점 후 결제 제한시간이 지난 HELD 좌석을 AVAILABLE로 복구한다.
     * Redis 락은 TTL로 자동 만료되지만 DB 상태는 그대로이므로, 주기적으로 호출해 둘을 일치시킨다.
     * 복구한 좌석마다 seat.released(TTL_EXPIRED)를 같은 트랜잭션에서 outbox에 기록한다.
     *
     * @param heldBefore 이 시각 이전에 선점된 좌석을 복구 대상으로 본다
     * @return 복구된 좌석 목록
     */
    List<SeatResponse> releaseExpiredSeats(LocalDateTime heldBefore);

    /**
     * 여러 회차의 좌석을 모두 삭제한다. 지난 회차 정리에서만 쓴다.
     *
     * @param eventIds 좌석을 지울 회차 ID 목록
     * @return 삭제된 좌석 수
     */
    int deleteSeatsOfEvents(List<Long> eventIds);
}
