package com.ticketing.api.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 실시간 알림용 STOMP over WebSocket 설정. 클라이언트는 "/ws"로 연결해서 "/topic/**"을 구독한다.
 * 서버 → 클라이언트 단방향 브로드캐스트만 하므로 별도 외부 브로커 없이 내장 SimpleBroker를 사용한다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOriginPatterns;

    // 기본값(빈 목록)이면 같은 Origin의 접속만 받는다. 화면을 다른 도메인에서 서비스할 때만 그 Origin을 명시한다.
    // 로컬 개발 서버(Vite)는 Host 헤더를 그대로 넘기는 프록시라 같은 Origin으로 판정되어 따로 열 필요가 없다.
    public WebSocketConfig(@Value("${websocket.allowed-origin-patterns:}") String[] allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 브라우저는 다른 사이트의 페이지에서도 WebSocket을 열 수 있으므로(CORS가 적용되지 않음), Origin 검사로 막는다.
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }
}
