package com.ticketing.api.domain.seat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.api.domain.queue.consumer.EntryGrantedRelayConsumer;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.seat.dto.SeatStatusChangedEvent;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 실제 STOMP 클라이언트로 접속해서, 좌석 상태 변경과 입장 허용 알림이 구독 토픽으로 전달되는지 검증한다.
 * 구독이 서버에 등록되기 전에 발행하면 유실되므로, 구독 후 잠시 대기하고 발행한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketBroadcastIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private EntryGrantedRelayConsumer entryGrantedRelayConsumer;

    // 좌석 상태 변경 이벤트가 해당 이벤트의 좌석 토픽 구독자에게 전달되어야 한다
    @Test
    @DisplayName("좌석 상태가 바뀌면 /topic/events/{eventId}/seats 구독자가 메시지를 받는다")
    void seatStatusChanged_isBroadcastToSubscribers() throws Exception {
        // given
        BlockingQueue<String> received = subscribe("/topic/events/7/seats");

        // when
        eventPublisher.publishEvent(new SeatStatusChangedEvent(7L, 42L, SeatStatus.HELD));

        // then
        String message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).contains("seatId=42").contains("status=HELD");
    }

    // 다른 이벤트의 좌석 변경은 구독 중인 토픽으로 오면 안 된다
    @Test
    @DisplayName("다른 이벤트의 좌석 변경은 전달되지 않는다")
    void seatStatusChanged_otherEvent_isNotReceived() throws Exception {
        // given
        BlockingQueue<String> received = subscribe("/topic/events/8/seats");

        // when
        eventPublisher.publishEvent(new SeatStatusChangedEvent(9L, 1L, SeatStatus.HELD));

        // then
        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }

    // 입장 허용 이벤트가 해당 requestId의 대기 화면 토픽으로 전달되어야 한다
    @Test
    @DisplayName("입장 허용 이벤트는 /topic/entries/{requestId} 구독자에게 전달된다")
    void entryGranted_isRelayedToRequester() throws Exception {
        // given
        EntryGrantedEvent event = EntryGrantedEvent.from(EntryRequestedEvent.of(1L));
        BlockingQueue<String> received = subscribe("/topic/entries/" + event.requestId());

        // when
        entryGrantedRelayConsumer.relay(event);

        // then
        assertThat(received.poll(5, TimeUnit.SECONDS)).contains(event.requestId());
    }

    private BlockingQueue<String> subscribe(String destination) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(payload.toString());
            }
        });
        Thread.sleep(500);
        return received;
    }
}
