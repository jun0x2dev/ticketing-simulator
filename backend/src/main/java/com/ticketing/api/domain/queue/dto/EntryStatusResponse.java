package com.ticketing.api.domain.queue.dto;

import com.ticketing.api.domain.queue.waiting.WaitingPosition;

/**
 * 입장 요청 상태 조회 응답 DTO.
 * 대기 중(WAITING)이면 대기 순번/뒤 인원/예상 대기 시간을, 허용(GRANTED)되면 token을 채운다.
 * 순번 정보가 없는 WAITING은 곧 입장 처리되거나(줄에서 막 빠짐) 알 수 없는 요청이다.
 */
public record EntryStatusResponse(
        String requestId,
        EntryStatus status,
        String token,
        Long position,
        Long behind,
        Long estimatedSeconds
) {

    /**
     * 대기 순번을 알 수 없는 대기 상태 응답을 생성한다.
     *
     * @param requestId 입장 요청 ID
     * @return WAITING 상태 응답
     */
    public static EntryStatusResponse waiting(String requestId) {
        return new EntryStatusResponse(requestId, EntryStatus.WAITING, null, null, null, null);
    }

    /**
     * 대기 순번이 있는 대기 상태 응답을 생성한다.
     *
     * @param requestId        입장 요청 ID
     * @param position         현재 대기 위치
     * @param estimatedSeconds 예상 대기 시간(초)
     * @return WAITING 상태 응답
     */
    public static EntryStatusResponse waiting(String requestId, WaitingPosition position, long estimatedSeconds) {
        return new EntryStatusResponse(requestId, EntryStatus.WAITING, null,
                position.position(), position.behind(), estimatedSeconds);
    }

    /**
     * 재처리까지 실패해 입장할 수 없게 된 상태의 응답을 생성한다. 클라이언트는 다시 입장 요청을 해야 한다.
     *
     * @param requestId 입장 요청 ID
     * @return FAILED 상태 응답
     */
    public static EntryStatusResponse failed(String requestId) {
        return new EntryStatusResponse(requestId, EntryStatus.FAILED, null, null, null, null);
    }

    /**
     * 입장이 허용된 상태의 응답을 생성한다.
     *
     * @param requestId 입장 요청 ID
     * @param token     발급된 입장 토큰
     * @return GRANTED 상태 응답
     */
    public static EntryStatusResponse granted(String requestId, String token) {
        return new EntryStatusResponse(requestId, EntryStatus.GRANTED, token, null, null, null);
    }
}
