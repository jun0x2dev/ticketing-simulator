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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EntryService 구현체. DB를 쓰지 않고 Redis 토큰과 Kafka 이벤트만 다룬다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntryServiceImpl implements EntryService {

    private static final Logger log = LoggerFactory.getLogger(EntryServiceImpl.class);

    private final EventService eventService;
    private final EntryRequestProducer entryRequestProducer;
    private final EntryGrantedProducer entryGrantedProducer;
    private final EntryTokenManager entryTokenManager;

    @Override
    public EntryRequestResponse requestEntry(Long eventId) {
        eventService.getEvent(eventId);
        EntryRequestedEvent event = EntryRequestedEvent.of(eventId);
        entryRequestProducer.send(event);
        return new EntryRequestResponse(event.requestId());
    }

    @Override
    public void grantEntry(EntryRequestedEvent event) {
        // 토큰 발급이 requestId 단위로 1회만 성공하므로, 재전달된 중복 메시지는 여기서 걸러진다.
        if (entryTokenManager.issue(event.requestId(), event.eventId()).isEmpty()) {
            log.info("이미 처리된 입장 요청 - requestId: {}", event.requestId());
            return;
        }
        entryGrantedProducer.send(EntryGrantedEvent.from(event));
        log.info("입장 허용 - requestId: {}, eventId: {}", event.requestId(), event.eventId());
    }

    @Override
    public EntryStatusResponse getEntryStatus(String requestId) {
        return entryTokenManager.findToken(requestId)
                .map(token -> EntryStatusResponse.granted(requestId, token))
                .orElseGet(() -> EntryStatusResponse.waiting(requestId));
    }
}
