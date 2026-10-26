package com.ticketing.api.domain.queue.dto;

/**
 * 입장 요청의 처리 상태. 존재하지 않는 requestId도 토큰이 없으므로 WAITING으로 응답한다.
 */
public enum EntryStatus {
    WAITING,
    GRANTED
}
