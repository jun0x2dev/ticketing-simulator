package com.ticketing.api.domain.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비즈니스 데이터와 같은 트랜잭션에 기록해 두었다가 커밋 후 Kafka로 발행할 이벤트 1건(Transactional Outbox).
 * DB 커밋과 Kafka 발행을 따로 하면 둘 중 하나만 반영될 수 있으므로, 발행할 내용을 먼저 DB에 남겨 유실을 막는다.
 */
@Entity
// 발행 대기 행 조회(published_at is null)와 발행 완료 행 정리(published_at < ?)가 모두 이 컬럼으로 대상을 찾는다.
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_events_published_at", columnList = "published_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String topic;

    // Kafka 파티션 키. 같은 키의 이벤트가 같은 파티션에 순서대로 쌓이게 한다.
    @Column(name = "message_key", nullable = false, length = 100)
    private String messageKey;

    // 발행 시 payload를 원래 DTO로 되돌리기 위한 클래스 이름. Consumer가 받는 타입 헤더를 기존과 같게 유지한다.
    @Column(name = "payload_type", nullable = false, length = 200)
    private String payloadType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // null이면 아직 발행되지 않은 이벤트다.
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // 발행 실패 횟수. 계속 실패하는 이벤트를 로그로 추적하기 위해 남긴다.
    @Column(nullable = false)
    private int attempts;

    private OutboxEvent(String topic, String messageKey, String payloadType, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payloadType = payloadType;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 발행 대기 상태의 이벤트를 생성한다.
     *
     * @param topic       발행할 Kafka 토픽
     * @param messageKey  Kafka 파티션 키
     * @param payloadType payload를 되돌릴 DTO 클래스 이름
     * @param payload     JSON으로 직렬화한 이벤트 내용
     * @return 생성된 OutboxEvent (publishedAt = null)
     */
    public static OutboxEvent create(String topic, String messageKey, String payloadType, String payload) {
        return new OutboxEvent(topic, messageKey, payloadType, payload);
    }

    /**
     * Kafka 브로커가 수신을 확인한 이벤트를 발행 완료로 표시한다.
     */
    public void markPublished() {
        this.publishedAt = LocalDateTime.now();
    }

    /**
     * 발행에 실패했음을 기록한다. 발행 대기 상태는 그대로 두어 다음 주기에 다시 시도한다.
     */
    public void recordFailure() {
        this.attempts++;
    }
}
