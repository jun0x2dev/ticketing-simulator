package com.ticketing.api.domain.queue.service.impl;

import com.ticketing.api.domain.event.service.EventService;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.queue.dto.EntryRequestResponse;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.dto.EntryStatusResponse;
import com.ticketing.api.domain.queue.producer.EntryGrantedProducer;
import com.ticketing.api.domain.queue.producer.EntryRequestProducer;
import com.ticketing.api.domain.queue.service.EntryService;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.domain.queue.waiting.WaitingLineManager;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EntryService 구현체. DB를 쓰지 않고 Redis 토큰과 Kafka 이벤트만 다룬다.
 */
@Service
@Transactional(readOnly = true)
public class EntryServiceImpl implements EntryService {

    private static final Logger log = LoggerFactory.getLogger(EntryServiceImpl.class);

    // DLT에서 원래 토픽으로 다시 보내는 최대 횟수. 일시적 장애는 한 번이면 복구되고, 계속 실패하는 요청이 무한히 돌지 않게 막는다.
    static final int MAX_REDRIVE = 1;

    private final EventService eventService;
    private final EntryRequestProducer entryRequestProducer;
    private final EntryGrantedProducer entryGrantedProducer;
    private final EntryTokenManager entryTokenManager;
    private final WaitingLineManager waitingLineManager;
    private final int workerTps;

    public EntryServiceImpl(EventService eventService,
                            EntryRequestProducer entryRequestProducer,
                            EntryGrantedProducer entryGrantedProducer,
                            EntryTokenManager entryTokenManager,
                            WaitingLineManager waitingLineManager,
                            @Value("${queue.worker.tps:100}") int workerTps) {
        this.eventService = eventService;
        this.entryRequestProducer = entryRequestProducer;
        this.entryGrantedProducer = entryGrantedProducer;
        this.entryTokenManager = entryTokenManager;
        this.waitingLineManager = waitingLineManager;
        this.workerTps = workerTps;
    }

    @Override
    public EntryRequestResponse requestEntry(Long eventId) {
        eventService.getEvent(eventId);
        EntryRequestedEvent event = EntryRequestedEvent.of(eventId);
        // Kafka 발행보다 먼저 줄에 세워야, Consumer가 먼저 처리해 버려 줄에 영원히 남는 요청이 생기지 않는다.
        waitingLineManager.join(eventId, event.requestId());
        publish(event);
        return new EntryRequestResponse(event.requestId());
    }

    @Override
    public void grantEntry(EntryRequestedEvent event) {
        // 토큰 발급이 requestId 단위로 1회만 성공하므로, 재전달된 중복 메시지는 여기서 걸러진다.
        boolean issued = entryTokenManager.issue(event.requestId(), event.eventId()).isPresent();
        // 중복 메시지여도 줄에서 빼는 건 안전하고, 이전 처리 중 줄에서 빼지 못한 경우를 정리한다.
        waitingLineManager.leave(event.eventId(), event.requestId());
        if (!issued) {
            log.info("이미 처리된 입장 요청 - requestId: {}", event.requestId());
            return;
        }
        entryGrantedProducer.send(EntryGrantedEvent.from(event));
        log.info("입장 허용 - requestId: {}, eventId: {}", event.requestId(), event.eventId());
    }

    @Override
    public void handleDeadLetter(EntryRequestedEvent event) {
        if (event.redriveCount() < MAX_REDRIVE) {
            publish(event.redriven());
            log.warn("DLT 입장 요청 재투입 - requestId: {}, redriveCount: {}", event.requestId(), event.redriveCount() + 1);
            return;
        }
        failRequest(event, null);
    }

    // 접수 응답을 늦추지 않도록 발행 결과를 기다리지 않는다. 대신 발행이 실패하면 줄에서 빼고 실패로 표시해,
    // 줄에는 있지만 Kafka에는 없는 요청이 순번이 멈춘 채 남지 않게 한다 (대기 화면은 FAILED를 받아 다시 줄 서기를 안내).
    private void publish(EntryRequestedEvent event) {
        CompletableFuture<?> result;
        try {
            result = entryRequestProducer.send(event);
        } catch (RuntimeException e) {
            result = CompletableFuture.failedFuture(e);
        }
        result.whenComplete((ignored, ex) -> {
            if (ex != null) {
                failRequest(event, ex);
            }
        });
    }

    // 더 처리할 수 없는 요청을 줄에서 빼고 실패로 표시해, 클라이언트가 순번이 멈춘 채 기다리지 않게 한다.
    private void failRequest(EntryRequestedEvent event, Throwable cause) {
        waitingLineManager.leave(event.eventId(), event.requestId());
        entryTokenManager.markFailed(event.requestId());
        log.error("입장 요청 최종 실패 - requestId: {}, eventId: {}, redriveCount: {}",
                event.requestId(), event.eventId(), event.redriveCount(), cause);
    }

    @Override
    public EntryStatusResponse grantEntryDirectly(Long eventId) {
        eventService.getEvent(eventId);
        String requestId = UUID.randomUUID().toString();
        String token = entryTokenManager.issue(requestId, eventId).orElseThrow();
        return EntryStatusResponse.granted(requestId, token);
    }

    @Override
    public EntryStatusResponse getEntryStatus(Long eventId, String requestId) {
        Optional<String> token = entryTokenManager.findToken(requestId);
        if (token.isPresent()) {
            return EntryStatusResponse.granted(requestId, token.get());
        }
        if (entryTokenManager.isFailed(requestId)) {
            return EntryStatusResponse.failed(requestId);
        }
        return waitingLineManager.findPosition(eventId, requestId)
                // 큐 워커가 초당 workerTps명씩 입장시키므로 내 순번까지 걸리는 시간을 올림으로 계산한다.
                .map(position -> EntryStatusResponse.waiting(requestId, position,
                        (position.position() + workerTps - 1) / workerTps))
                .orElseGet(() -> EntryStatusResponse.waiting(requestId));
    }
}
