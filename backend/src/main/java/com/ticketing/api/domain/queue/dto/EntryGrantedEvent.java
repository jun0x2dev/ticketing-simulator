package com.ticketing.api.domain.queue.dto;

import java.time.Instant;

/**
 * "entry.granted" 토픽으로 발행되는 입장 허용 이벤트.
 * 입장 토큰 자체는 비밀값이므로 이벤트에 싣지 않고, 클라이언트가 requestId로 별도 조회한다.
 */
public record EntryGrantedEvent(
        String requestId,
        Long eventId,
        Instant grantedAt
) {

    /**
     * 입장 요청에 대한 입장 허용 이벤트를 생성한다.
     *
     * @param request 허용된 입장 요청
     * @return 생성된 이벤트
     */
    public static EntryGrantedEvent from(EntryRequestedEvent request) {
        return new EntryGrantedEvent(request.requestId(), request.eventId(), Instant.now());
    }
}
