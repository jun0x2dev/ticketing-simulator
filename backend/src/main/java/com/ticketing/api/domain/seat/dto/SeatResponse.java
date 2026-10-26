package com.ticketing.api.domain.seat.dto;

import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.entity.SeatStatus;

/**
 * 좌석 조회/선점 응답 DTO.
 */
public record SeatResponse(
        Long id,
        Long eventId,
        int seatNumber,
        SeatStatus status
) {

    /**
     * Seat 엔티티를 SeatResponse로 변환한다.
     *
     * @param seat 변환할 엔티티
     * @return 변환된 응답 DTO
     */
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getEvent().getId(), seat.getSeatNumber(), seat.getStatus());
    }
}
