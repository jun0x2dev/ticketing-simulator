# 입력 검증 실패가 500으로 응답됨

## 증상

k6 부하 시나리오의 이벤트 생성 요청이 `500 INTERNAL_SERVER_ERROR`로 실패했다. 로그에는 `MethodArgumentNotValidException`(`@Future openAt` 위반)과,
이어지는 좌석 조회의 `MethodArgumentTypeMismatchException`(`eventId=undefined`)이 `Unhandled exception`으로 찍혔다.

## 원인

- `GlobalExceptionHandler`가 `CustomException`과 `Exception`만 처리해서, Spring MVC의 요청 바인딩/검증 예외가 모두 500으로 떨어졌다. 클라이언트 입력 오류를 서버 오류로 응답하는 버그다.
- 직접적인 계기는 k6 스크립트가 `openAt`을 UTC 기준 "1시간 뒤"로 만든 것이다. 서버는 KST라 9시간 앞서 있어 과거 시각으로 판단했다.

## 해결

- `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`을 `INVALID_REQUEST`(400)로 응답하는 핸들러를 추가하고 `GlobalExceptionHandlerTest`(MockMvc)로 검증했다.
- k6 스크립트의 `openAt`을 이틀 뒤로 잡아 시간대 차이와 무관하게 했다.
