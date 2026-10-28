package com.ticketing.api.global.config;

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

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 로컬 시뮬레이터라 프론트 개발 서버(다른 포트)에서의 접속을 막지 않는다. 배포 시에는 허용 Origin을 제한한다.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }
}
