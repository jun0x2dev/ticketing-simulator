package com.ticketing.api.domain.seat.dto;

import com.ticketing.api.domain.seat.entity.SeatStatus;

/**
 * 좌석 상태가 바뀌었음을 알리는 이벤트. 트랜잭션 커밋 이후 WebSocket으로 브로드캐스트되며,
 * 클라이언트에 전달되는 메시지 본문으로도 그대로 사용한다.
 */
public record SeatStatusChangedEvent(
        Long eventId,
        Long seatId,
        SeatStatus status
) {
}
