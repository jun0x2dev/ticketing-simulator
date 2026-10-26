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
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.EVENT_NOT_FOUND));
        return EventResponse.from(event);
    }
}
