package com.ticketing.api.domain.event.dto;

import com.ticketing.api.domain.event.entity.Event;
import java.time.LocalDateTime;

/**
 * 이벤트 조회/생성 응답 DTO. Entity를 API 응답으로 직접 노출하지 않기 위한 변환 계층.
 */
public record EventResponse(
        Long id,
        String name,
        int seatCount,
        LocalDateTime openAt
) {

    /**
     * Event 엔티티를 EventResponse로 변환한다.
     *
     * @param event 변환할 엔티티
     * @return 변환된 응답 DTO
     */
    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getName(), event.getSeatCount(), event.getOpenAt());
    }
}
