package com.ticketing.api.domain.outbox.service;

import java.time.LocalDateTime;

/**
 * Transactional Outbox 기록/발행/정리 비즈니스 로직 인터페이스.
 */
public interface OutboxService {

    /**
     * 발행할 이벤트를 현재 트랜잭션에 함께 기록한다. 호출한 쪽의 트랜잭션이 커밋되어야만 발행 대상이 되고,
     * 롤백되면 이벤트도 함께 사라진다. 트랜잭션 밖에서 호출하면 예외가 발생한다.
     *
     * @param topic   발행할 Kafka 토픽
     * @param key     Kafka 파티션 키
     * @param payload 발행할 이벤트 DTO
     */
    void append(String topic, String key, Object payload);

    /**
     * 발행 대기 이벤트를 기록된 순서대로 최대 batchSize건 Kafka로 발행한다.
     * 브로커가 수신을 확인한 건만 발행 완료로 표시하고, 실패한 건은 다음 호출에서 다시 시도한다.
     *
     * @param batchSize 한 번에 발행할 최대 건수
     * @return 발행 완료로 표시한 이벤트 수 (batchSize와 같으면 남은 대상이 더 있을 수 있고, 작으면 남은 대상이 없거나 실패가 있었다)
     */
    int relayPending(int batchSize);

    /**
     * 기준 시각 이전에 발행 완료된 이벤트를 최대 batchSize건 삭제한다.
     *
     * @param publishedBefore 이 시각 이전에 발행된 이벤트가 대상
     * @param batchSize       한 번에 삭제할 최대 건수
     * @return 삭제한 이벤트 수
     */
    int deletePublishedBefore(LocalDateTime publishedBefore, int batchSize);
}
