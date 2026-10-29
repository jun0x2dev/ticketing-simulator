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
    public static final String ENTRY_REQUESTED_DLT = ENTRY_REQUESTED + ".DLT";
    public static final String ENTRY_GRANTED = "entry.granted";
    public static final String SEAT_CONFIRMED = "seat.confirmed";
    public static final String SEAT_RELEASED = "seat.released";
    // 통계 Consumer 등 하위 Consumer가 재시도 끝에 실패한 메시지를 모으는 DLT. 공통 에러 핸들러가 "{원본 토픽}.DLT"로 보낸다.
    public static final String ENTRY_GRANTED_DLT = ENTRY_GRANTED + ".DLT";
    public static final String SEAT_CONFIRMED_DLT = SEAT_CONFIRMED + ".DLT";
    public static final String SEAT_RELEASED_DLT = SEAT_RELEASED + ".DLT";

    /** 입장 요청 이벤트 토픽. 파티션 키는 eventId. */
    @Bean
    public NewTopic entryRequestedTopic() {
        return TopicBuilder.name(ENTRY_REQUESTED).partitions(3).replicas(1).build();
    }

    /** 입장 요청 처리에 최대 재시도 후에도 실패한 메시지가 모이는 Dead Letter 토픽. */
    @Bean
    public NewTopic entryRequestedDltTopic() {
        return TopicBuilder.name(ENTRY_REQUESTED_DLT).partitions(3).replicas(1).build();
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

    /** entry.granted 처리(통계/WebSocket 릴레이) 실패 메시지용 DLT. */
    @Bean
    public NewTopic entryGrantedDltTopic() {
        return TopicBuilder.name(ENTRY_GRANTED_DLT).partitions(3).replicas(1).build();
    }

    /** seat.confirmed 처리(통계) 실패 메시지용 DLT. */
    @Bean
    public NewTopic seatConfirmedDltTopic() {
        return TopicBuilder.name(SEAT_CONFIRMED_DLT).partitions(3).replicas(1).build();
    }

    /** seat.released 처리(통계) 실패 메시지용 DLT. */
    @Bean
    public NewTopic seatReleasedDltTopic() {
        return TopicBuilder.name(SEAT_RELEASED_DLT).partitions(3).replicas(1).build();
    }
}
