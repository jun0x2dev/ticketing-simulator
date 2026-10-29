package com.ticketing.api.domain.seat.scheduler;

import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.service.SeatService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 제한시간(TTL)이 지난 선점 좌석을 주기적으로 AVAILABLE로 복구하는 스케줄러.
 * Redis 락은 TTL로 스스로 풀리지만 DB의 HELD 상태는 남기 때문에, 이 작업이 없으면 좌석이 영구히 잠긴다.
 * 복구된 좌석의 seat.released(TTL_EXPIRED)는 복구 트랜잭션에서 outbox에 기록되어 발행된다.
 */
@Component
@RequiredArgsConstructor
public class SeatHoldExpirationScheduler {

    private final SeatService seatService;

    /**
     * 이전 실행이 끝난 뒤 5초 간격으로 만료 좌석을 복구한다 (fixedDelay라 실행이 겹치지 않는다).
     */
    @Scheduled(fixedDelay = 5000)
    public void releaseExpiredSeats() {
        seatService.releaseExpiredSeats(LocalDateTime.now().minus(Seat.HOLD_TTL));
    }
}
