package com.ticketing.api.domain.outbox.service.impl;

import com.ticketing.api.domain.outbox.entity.OutboxEvent;
import com.ticketing.api.domain.outbox.repository.OutboxEventRepository;
import com.ticketing.api.domain.outbox.service.OutboxService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * OutboxService 구현체. 이벤트는 호출한 쪽의 트랜잭션에 기록하고, 발행은 별도 트랜잭션에서 행 잠금으로 가져와 처리한다.
 * 발행은 "적어도 한 번"을 보장한다. 브로커 응답 전에 대기 시간이 끝난 건은 다음 주기에 다시 보내므로 중복이 생길 수 있고,
 * 이 토픽의 Consumer는 멱등 키로 중복을 걸러낸다.
 */
@Service
@Transactional(readOnly = true)
public class OutboxServiceImpl implements OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxServiceImpl.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final Duration sendTimeout;

    public OutboxServiceImpl(OutboxEventRepository outboxEventRepository,
                             KafkaTemplate<String, Object> kafkaTemplate,
                             JsonMapper jsonMapper,
                             @Value("${outbox.relay.send-timeout-ms:5000}") long sendTimeoutMillis) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.sendTimeout = Duration.ofMillis(sendTimeoutMillis);
    }

    @Override
    // 비즈니스 변경과 같은 트랜잭션이어야 의미가 있으므로, 트랜잭션 없이 호출되면 새로 만들지 않고 실패시킨다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String key, Object payload) {
        outboxEventRepository.save(OutboxEvent.create(
                topic, key, payload.getClass().getName(), jsonMapper.writeValueAsString(payload)));
    }

    @Override
    @Transactional
    public int relayPending(int batchSize) {
        List<OutboxEvent> pending = outboxEventRepository.findPendingForUpdate(PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return 0;
        }

        // 하나씩 응답을 기다리지 않고 모두 보낸 뒤 결과를 모은다. 같은 키의 순서는 Producer가 파티션 단위로 지킨다.
        List<CompletableFuture<?>> results = pending.stream().map(this::send).toList();

        // 행 잠금과 DB 커넥션을 잡은 채 기다리므로, 브로커가 응답하지 않아도 전체 대기 시간은 sendTimeout을 넘지 않게 한다.
        long deadline = System.nanoTime() + sendTimeout.toNanos();
        int published = 0;
        int failed = 0;
        Throwable firstCause = null;
        for (int i = 0; i < pending.size(); i++) {
            OutboxEvent event = pending.get(i);
            try {
                results.get(i).get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                event.markPublished();
                published++;
            } catch (ExecutionException | TimeoutException e) {
                event.recordFailure();
                failed++;
                if (firstCause == null) {
                    firstCause = e instanceof ExecutionException ? e.getCause() : e;
                }
            } catch (InterruptedException e) {
                // 종료 중이면 남은 건은 발행 대기로 두고 끝낸다. 다음 기동 후 다시 발행된다.
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (failed > 0) {
            // 브로커 장애 시 건마다 로그를 남기면 주기마다 수백 줄이 쌓이므로 주기당 한 번만 남긴다.
            log.error("outbox 발행 실패 - failed: {}/{}, firstId: {}", failed, pending.size(), pending.get(0).getId(), firstCause);
        }
        return published;
    }

    @Override
    @Transactional
    public int deletePublishedBefore(LocalDateTime publishedBefore, int batchSize) {
        List<Long> ids = outboxEventRepository.findIdsPublishedBefore(publishedBefore, PageRequest.of(0, batchSize));
        if (!ids.isEmpty()) {
            outboxEventRepository.deleteAllByIdInBatch(ids);
            log.info("발행 완료 outbox 정리 - count: {}", ids.size());
        }
        return ids.size();
    }

    // 저장해 둔 JSON을 원래 DTO로 되돌려 보낸다. 객체로 보내야 Kafka 직렬화기가 기존과 같은 타입 헤더를 붙인다.
    private CompletableFuture<?> send(OutboxEvent event) {
        try {
            Object payload = jsonMapper.readValue(event.getPayload(), Class.forName(event.getPayloadType()));
            return kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload);
        } catch (ClassNotFoundException | RuntimeException e) {
            // 역직렬화 실패나 send() 자체의 예외도 이 건의 실패로만 처리해서 같은 묶음의 다른 이벤트 발행을 막지 않는다.
            return CompletableFuture.failedFuture(e);
        }
    }
}
