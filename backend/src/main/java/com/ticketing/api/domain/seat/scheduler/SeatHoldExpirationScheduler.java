package com.ticketing.api.domain.seat.scheduler;

import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.payment.producer.SeatEventProducer;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.service.SeatService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 제한시간(TTL)이 지난 선점 좌석을 주기적으로 AVAILABLE로 복구하는 스케줄러.
 * Redis 락은 TTL로 스스로 풀리지만 DB의 HELD 상태는 남기 때문에, 이 작업이 없으면 좌석이 영구히 잠긴다.
 * 복구된 좌석은 seat.released(TTL_EXPIRED)로 발행한다.
 */
@Component
@RequiredArgsConstructor
public class SeatHoldExpirationScheduler {

    private final SeatService seatService;
    private final SeatEventProducer seatEventProducer;

    /**
     * 이전 실행이 끝난 뒤 5초 간격으로 만료 좌석을 복구한다 (fixedDelay라 실행이 겹치지 않는다).
     */
    @Scheduled(fixedDelay = 5000)
    public void releaseExpiredSeats() {
        // 복구 트랜잭션이 커밋된 뒤에 이벤트를 발행해서, 롤백된 복구가 seat.released로 나가지 않게 한다.
        seatService.releaseExpiredSeats(LocalDateTime.now().minus(Seat.HOLD_TTL)).forEach(seat ->
                seatEventProducer.sendReleased(SeatReleasedEvent.ttlExpired(seat.eventId(), seat.id())));
    }
}
