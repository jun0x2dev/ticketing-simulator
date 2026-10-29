package com.ticketing.api.global.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@code @Scheduled} 작업(TTL 복구, outbox 발행/정리, 회차 정리) 전용 스케줄러 설정.
 * WebSocket(STOMP) 설정이 하트비트용 TaskScheduler 빈을 만들면 Spring Boot가 기본 스케줄러를 만들지 않아,
 * {@code @Scheduled} 작업이 STOMP 스레드에서 돌게 된다. 그러면 브로커 응답을 기다리는 outbox 발행이 하트비트를 밀어낼 수 있어 분리한다.
 */
@Configuration
public class SchedulingConfig {

    /**
     * {@code @Scheduled}가 이름("taskScheduler")으로 찾아 쓰는 스케줄러. 풀 크기와 스레드 이름은
     * {@code spring.task.scheduling.*} 설정을 따른다.
     *
     * @param builder Spring Boot가 설정값을 반영해 만들어 둔 스케줄러 빌더
     * @return {@code @Scheduled} 전용 스케줄러
     */
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }
}
