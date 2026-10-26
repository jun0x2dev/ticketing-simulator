package com.ticketing.api.domain.event.dto;

import com.ticketing.api.domain.event.entity.Event;
import java.time.LocalDateTime;

public record EventResponse(
        Long id,
        String name,
        int seatCount,
        LocalDateTime openAt
) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getSeatCount(), event.getOpenAt());
    }
}
