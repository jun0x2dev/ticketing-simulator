package com.ticketing.api.global.exception;

import com.ticketing.api.global.response.ApiResponse;
import com.ticketing.api.global.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * 클라이언트 입력 오류(Bean Validation 실패, 읽을 수 없는 본문, 잘못된 파라미터 타입)를
     * 500이 아닌 INVALID_REQUEST(400)로 응답한다.
     *
     * @param e 요청 바인딩/검증 단계에서 발생한 예외
     * @return INVALID_REQUEST 응답
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception e) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        log.warn("Invalid request: {}", e.getMessage());
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
