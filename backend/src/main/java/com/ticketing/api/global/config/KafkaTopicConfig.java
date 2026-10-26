package com.ticketing.api.global.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 티케팅 시뮬레이터에서 사용하는 Kafka 토픽들을 정의하는 설정 클래스.
 * KafkaAdmin이 애플리케이션 시작 시 이 토픽들을 자동으로 생성한다.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String ENTRY_REQUESTED = "entry.requested";
    public static final String ENTRY_GRANTED = "entry.granted";
    public static final String SEAT_CONFIRMED = "seat.confirmed";
    public static final String SEAT_RELEASED = "seat.released";

    /** 입장 요청 이벤트 토픽. 파티션 키는 eventId. */
    @Bean
    public NewTopic entryRequestedTopic() {
        return TopicBuilder.name(ENTRY_REQUESTED).partitions(3).replicas(1).build();
    }

    /** 입장 허용 이벤트 토픽 (대기열 처리 결과). */
    @Bean
    public NewTopic entryGrantedTopic() {
        return TopicBuilder.name(ENTRY_GRANTED).partitions(3).replicas(1).build();
    }

    /** 결제 완료로 좌석이 확정된 이벤트 토픽. */
    @Bean
    public NewTopic seatConfirmedTopic() {
        return TopicBuilder.name(SEAT_CONFIRMED).partitions(3).replicas(1).build();
    }

    /** 결제 TTL 만료 등으로 좌석이 해제된 이벤트 토픽. */
    @Bean
    public NewTopic seatReleasedTopic() {
        return TopicBuilder.name(SEAT_RELEASED).partitions(3).replicas(1).build();
    }
}
