package com.ticketing.api.global.exception;

import com.ticketing.api.global.response.ApiResponse;
import com.ticketing.api.global.response.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * 없는 경로는 404(ENTITY_NOT_FOUND)로 응답한다. 아래 Exception 처리기에 잡혀 500이 되지 않도록 따로 처리한다.
     *
     * @param e 매핑된 핸들러도 정적 리소스도 없을 때의 예외
     * @return ENTITY_NOT_FOUND 응답
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return clientError(ErrorCode.ENTITY_NOT_FOUND, e);
    }

    /**
     * 경로는 있지만 메서드가 다른 요청은 405(METHOD_NOT_ALLOWED)로 응답한다.
     * 예: 로컬 전용 {@code POST .../entries/direct}가 꺼진 환경에서는 {@code GET .../entries/{requestId}}와 경로만 겹친다.
     *
     * @param e 지원하지 않는 HTTP 메서드 예외
     * @return METHOD_NOT_ALLOWED 응답
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return clientError(ErrorCode.METHOD_NOT_ALLOWED, e);
    }

    private ResponseEntity<ApiResponse<Void>> clientError(ErrorCode errorCode, Exception e) {
        log.warn("{}: {}", errorCode, e.getMessage());
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
