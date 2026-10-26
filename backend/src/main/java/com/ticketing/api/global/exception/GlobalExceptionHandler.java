package com.ticketing.api.global.exception;

import com.ticketing.api.global.response.ApiResponse;
import com.ticketing.api.global.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리기. CustomException은 warn 레벨로, 그 외 예상치 못한 예외는
 * error 레벨 + 스택트레이스로 로깅한 뒤 공통 응답 포맷으로 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("CustomException: {}", errorCode, e);
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.failure(new ErrorResponse(errorCode.name(), errorCode.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.failure(new ErrorResponse(errorCode.name(), errorCode.getMessage())));
    }
}
