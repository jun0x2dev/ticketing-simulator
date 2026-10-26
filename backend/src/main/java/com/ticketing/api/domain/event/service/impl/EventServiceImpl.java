package com.ticketing.api.domain.event.service.impl;

import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;
import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.event.service.EventService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EventService 구현체. 기본적으로 읽기 전용 트랜잭션을 적용하고,
 * 쓰기 작업(createEvent)에만 별도로 @Transactional을 명시한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private static final Logger log = LoggerFactory.getLogger(EventServiceImpl.class);

    private final EventRepository eventRepository;

    @Override
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        Event event = Event.create(request.name(), request.seatCount(), request.openAt());
        Event saved = eventRepository.save(event);
        log.info("이벤트 생성 완료 - eventId: {}, name: {}", saved.getId(), saved.getName());
        return EventResponse.from(saved);
    }

    @Override
    public EventResponse getEvent(Long eventId) {
        // 존재하지 않는 이벤트 조회 시 404에 해당하는 CustomException으로 변환
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.EVENT_NOT_FOUND));
        return EventResponse.from(event);
    }
}
