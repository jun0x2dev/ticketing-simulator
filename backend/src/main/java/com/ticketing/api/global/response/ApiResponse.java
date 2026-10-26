package com.ticketing.api.global.response;

/**
 * 모든 API가 공통으로 사용하는 응답 포맷.
 * success가 true면 data에 값이, false면 error에 상세 정보가 담긴다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    /**
     * 성공 응답을 생성한다.
     *
     * @param data 응답 데이터
     * @return 성공 응답
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 실패 응답을 생성한다.
     *
     * @param error 에러 코드/메시지
     * @return 실패 응답
     */
    public static <T> ApiResponse<T> failure(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
