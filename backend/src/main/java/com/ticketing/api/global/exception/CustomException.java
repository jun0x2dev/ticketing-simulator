package com.ticketing.api.global.exception;

/**
 * 도메인 로직에서 의도적으로 던지는 예외. ErrorCode를 가지고 있어
 * GlobalExceptionHandler가 적절한 HTTP 상태/메시지로 변환할 수 있다.
 */
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
