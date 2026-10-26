package com.ticketing.api.domain.queue.dto;

import java.time.Instant;
import java.util.UUID;

public record EntryRequestedEvent(
        String requestId,
        Long eventId,
        Instant requestedAt
) {

    public static EntryRequestedEvent of(Long eventId) {
        return new EntryRequestedEvent(UUID.randomUUID().toString(), eventId, Instant.now());
    }
}
