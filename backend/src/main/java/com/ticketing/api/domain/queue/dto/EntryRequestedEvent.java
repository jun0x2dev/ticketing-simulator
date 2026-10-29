package com.ticketing.api.domain.queue.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * "entry.requested" 토픽으로 발행되는 입장 요청 이벤트.
 * requestId는 멱등성 처리(중복 소비 방지)를 위한 고유 식별자이고, redriveCount는 DLT에서 다시 보낸 횟수다.
 */
public record EntryRequestedEvent(
        String requestId,
        Long eventId,
        Instant requestedAt,
        int redriveCount
) {

    /**
     * 새 입장 요청 이벤트를 생성한다. requestId는 UUID로 자동 발급된다.
     *
     * @param eventId 입장을 요청하는 대상 이벤트(공연) ID
     * @return 생성된 이벤트
     */
    public static EntryRequestedEvent of(Long eventId) {
        return new EntryRequestedEvent(UUID.randomUUID().toString(), eventId, Instant.now(), 0);
    }

    /**
     * DLT에서 원래 토픽으로 다시 보낼 때 쓰는 사본을 만든다. 재투입 횟수만 1 늘리고 requestId는 유지해서
     * 토큰 발급 멱등성(requestId 단위)이 그대로 적용되게 한다.
     *
     * @return 재투입 횟수가 1 증가한 이벤트
     */
    public EntryRequestedEvent redriven() {
        return new EntryRequestedEvent(requestId, eventId, requestedAt, redriveCount + 1);
    }
}
