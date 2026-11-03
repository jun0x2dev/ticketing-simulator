package com.ticketing.api.global.exception;

import com.ticketing.api.global.response.ApiResponse;
import com.ticketing.api.global.response.ErrorResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외 처리기. CustomException은 warn 레벨로, 그 외 예상치 못한 예외는
 * error 레벨 + 스택트레이스로 로깅한 뒤 공통 응답 포맷으로 변환한다.
 * Spring MVC 표준 예외(검증 실패, 없는 경로, 지원하지 않는 메서드/Content-Type 등)는 상위 클래스가 정한
 * 상태 코드와 헤더(Allow, Accept 등)를 그대로 쓰고 본문만 공통 포맷으로 바꾼다. 하나씩 처리기를 두면
 * 빠진 예외가 아래 Exception 처리기에 잡혀 클라이언트 오류가 500이 되기 때문이다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 표준 예외의 상태 코드별 응답 코드. 여기 없는 4xx는 INVALID_REQUEST로 응답한다.
    private static final Map<HttpStatus, ErrorCode> STANDARD_ERROR_CODES = Map.of(
            HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE);

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("CustomException: {}", errorCode, e);
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.failure(new ErrorResponse(errorCode.name(), errorCode.getMessage())));
    }

    /**
     * Spring MVC 표준 예외의 응답 본문을 공통 포맷으로 바꾼다. 상태 코드와 헤더는 상위 클래스가 만든 것을 유지한다.
     *
     * @param ex         처리 중인 표준 예외
     * @param body       상위 클래스가 만든 본문(ProblemDetail). 공통 포맷으로 대체한다
     * @param headers    응답 헤더
     * @param statusCode 상위 클래스가 정한 상태 코드
     * @param request    현재 요청
     * @return 공통 포맷 응답. 응답이 이미 커밋되어 쓸 수 없으면 null
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null) {
            return null;
        }
        ErrorCode errorCode;
        if (statusCode.is5xxServerError()) {
            errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
            log.error("Spring MVC exception", ex);
        } else {
            errorCode = STANDARD_ERROR_CODES.getOrDefault(HttpStatus.resolve(statusCode.value()), ErrorCode.INVALID_REQUEST);
            log.warn("{}: {}", errorCode, ex.getMessage());
        }
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
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
