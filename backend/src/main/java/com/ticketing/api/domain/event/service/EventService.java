package com.ticketing.api.domain.event.service;

import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;

/**
 * 이벤트(공연/행사) 생성 및 조회 비즈니스 로직 인터페이스.
 */
public interface EventService {

    /**
     * 새 이벤트를 생성한다.
     *
     * @param request 생성 요청 정보
     * @return 생성된 이벤트 응답
     */
    EventResponse createEvent(EventCreateRequest request);

    /**
     * 이벤트를 단건 조회한다.
     *
     * @param eventId 조회할 이벤트 ID
     * @return 이벤트 응답
     */
    EventResponse getEvent(Long eventId);
}
