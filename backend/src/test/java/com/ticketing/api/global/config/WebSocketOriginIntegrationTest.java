package com.ticketing.api.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * 허용 Origin을 따로 설정하지 않았을 때 STOMP 엔드포인트가 같은 Origin의 접속만 받는지 검증한다.
 * 브라우저는 다른 사이트의 페이지에서도 WebSocket을 열 수 있으므로, Origin 검사가 없으면 아무 사이트나 알림을 엿들을 수 있다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketOriginIntegrationTest {

    @LocalServerPort
    private int port;

    // 화면과 같은 Origin(개발 서버 프록시 포함)에서의 접속은 받아야 한다
    @Test
    @DisplayName("같은 Origin에서의 WebSocket 접속은 허용한다")
    void sameOrigin_isAccepted() throws Exception {
        // when
        StompSession session = connect("http://localhost:" + port);

        // then
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    // 다른 사이트에서 연 WebSocket은 핸드셰이크 단계에서 거절되어야 한다
    @Test
    @DisplayName("다른 Origin에서의 WebSocket 접속은 거절한다")
    void foreignOrigin_isRejected() {
        assertThatThrownBy(() -> connect("http://evil.example"))
                .isInstanceOf(ExecutionException.class);
    }

    private StompSession connect(String origin) throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws", headers, new StompSessionHandlerAdapter() { })
                .get(5, TimeUnit.SECONDS);
    }
}
