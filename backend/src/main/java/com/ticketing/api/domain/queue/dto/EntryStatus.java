package com.ticketing.api.domain.queue.dto;

/**
 * 입장 요청의 처리 상태. 존재하지 않는 requestId도 토큰이 없으므로 WAITING으로 응답한다.
 * FAILED는 DLT 재처리까지 실패해 다시 입장 요청을 해야 하는 상태다.
 */
public enum EntryStatus {
    WAITING,
    GRANTED,
    FAILED
}
