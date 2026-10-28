package com.ticketing.api.domain.seat.broadcast;

import com.ticketing.api.domain.seat.dto.SeatStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 좌석 상태 변경을 해당 이벤트의 좌석 토픽("/topic/events/{eventId}/seats")으로 브로드캐스트한다.
 */
@Component
@RequiredArgsConstructor
public class SeatStatusBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 좌석 상태 변경 이벤트를 구독 중인 클라이언트에게 전달한다.
     * 커밋 이후에만 실행해서, 롤백된 변경이 화면에 반영되거나 클라이언트가 커밋 전 상태를 읽는 일을 막는다.
     *
     * @param event 상태가 바뀐 좌석 정보
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void broadcast(SeatStatusChangedEvent event) {
        messagingTemplate.convertAndSend("/topic/events/" + event.eventId() + "/seats", event);
    }
}
