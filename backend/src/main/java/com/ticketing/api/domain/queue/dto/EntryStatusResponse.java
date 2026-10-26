package com.ticketing.api.domain.queue.dto;

/**
 * 입장 요청 상태 조회 응답 DTO. 입장이 허용(GRANTED)된 경우에만 token이 채워진다.
 */
public record EntryStatusResponse(
        String requestId,
        EntryStatus status,
        String token
) {

    /**
     * 아직 입장 허용 전인 상태의 응답을 생성한다.
     *
     * @param requestId 입장 요청 ID
     * @return WAITING 상태 응답
     */
    public static EntryStatusResponse waiting(String requestId) {
        return new EntryStatusResponse(requestId, EntryStatus.WAITING, null);
    }

    /**
     * 입장이 허용된 상태의 응답을 생성한다.
     *
     * @param requestId 입장 요청 ID
     * @param token     발급된 입장 토큰
     * @return GRANTED 상태 응답
     */
    public static EntryStatusResponse granted(String requestId, String token) {
        return new EntryStatusResponse(requestId, EntryStatus.GRANTED, token);
    }
}
