package com.ticketing.api.domain.queue.dto;

/**
 * 입장 요청 접수 응답 DTO. 클라이언트는 requestId로 입장 허용 여부를 조회한다.
 */
public record EntryRequestResponse(
        String requestId
) {
}
