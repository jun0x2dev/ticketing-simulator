package com.ticketing.api.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인 전역에서 사용하는 에러 코드 정의. 각 코드는 HTTP 상태와 기본 메시지를 가진다.
 */
public enum ErrorCode {
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점된 좌석입니다."),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다."),
    SEAT_NOT_HELD(HttpStatus.CONFLICT, "선점 상태가 아닌 좌석입니다."),
    SEAT_HOLD_EXPIRED(HttpStatus.CONFLICT, "결제 제한시간이 지나 좌석 선점이 만료되었습니다."),
    SEAT_HOLDER_MISMATCH(HttpStatus.FORBIDDEN, "이 좌석을 선점한 사용자가 아닙니다."),
    SEAT_HOLD_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "이미 선점 중인 좌석이 있습니다. 한 번에 1석만 선점할 수 있습니다."),
    ENTRY_TOKEN_INVALID(HttpStatus.FORBIDDEN, "유효하지 않거나 만료된 입장 토큰입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
