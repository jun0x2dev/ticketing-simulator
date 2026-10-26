package com.ticketing.api.domain.queue.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * "entry.requested" 토픽으로 발행되는 입장 요청 이벤트.
 * requestId는 멱등성 처리(중복 소비 방지)를 위한 고유 식별자로 쓰일 예정이다.
 */
public record EntryRequestedEvent(
        String requestId,
        Long eventId,
        Instant requestedAt
) {

    /**
     * 새 입장 요청 이벤트를 생성한다. requestId는 UUID로 자동 발급된다.
     *
     * @param eventId 입장을 요청하는 대상 이벤트(공연) ID
     * @return 생성된 이벤트
     */
    public static EntryRequestedEvent of(Long eventId) {
        return new EntryRequestedEvent(UUID.randomUUID().toString(), eventId, Instant.now());
    }
}
