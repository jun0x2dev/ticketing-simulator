package com.ticketing.api.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인 전역에서 사용하는 에러 코드 정의. 각 코드는 HTTP 상태와 기본 메시지를 가진다.
 */
public enum ErrorCode {
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    SEAT_ALREADY_HELD(HttpStatus.CONFLICT, "이미 선점된 좌석입니다."),
    SEAT_NOT_FOUND(HttpStatus.NOT_FOUND, "좌석을 찾을 수 없습니다."),
    SEAT_NOT_HELD(HttpStatus.CONFLICT, "선점 상태가 아닌 좌석입니다."),
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
