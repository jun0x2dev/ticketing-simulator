package com.ticketing.api.global.response;

/**
 * 실패 응답에 담기는 에러 상세 정보. code는 ErrorCode의 enum 이름을 그대로 사용한다.
 */
public record ErrorResponse(String code, String message) {
}
