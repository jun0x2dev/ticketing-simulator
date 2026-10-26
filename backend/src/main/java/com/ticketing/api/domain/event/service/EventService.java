package com.ticketing.api.domain.event.service;

import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;

public interface EventService {

    EventResponse createEvent(EventCreateRequest request);

    EventResponse getEvent(Long eventId);
}
