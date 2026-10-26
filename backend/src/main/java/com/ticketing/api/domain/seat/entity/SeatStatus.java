package com.ticketing.api.domain.seat.entity;

/**
 * 좌석 상태.
 * AVAILABLE: 선점 가능, HELD: Redis 락으로 선점되어 결제 대기 중, CONFIRMED: 결제 완료로 확정.
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    CONFIRMED
}
