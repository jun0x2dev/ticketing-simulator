# 처리기가 없는 Spring MVC 표준 예외가 500으로 응답됨

## 증상

- `POST /api/v1/events`를 `Content-Type: text/plain`으로 보내면 415가 아니라 `500 INTERNAL_SERVER_ERROR`가 나왔다. 로그에는 `HttpMediaTypeNotSupportedException`이 `Unhandled exception`으로 찍혔다.
- 405 응답에는 허용 메서드를 알리는 `Allow` 헤더가 없었다.
- TIL을 정리하며 외부 검토를 받다가 지적받았다. 이전 두 번의 수정(검증 예외 400, 없는 경로 404/메서드 405)이 모두 예외를 하나씩 추가하는 방식이라 남은 예외가 있었다.

## 원인

- 전역 처리기의 `@ExceptionHandler(Exception.class)`가 Spring MVC의 기본 처리(`DefaultHandlerExceptionResolver`)보다 먼저 모든 예외를 잡는다. 처리기를 따로 두지 않은 표준 예외(415, 406, 필수 파라미터 누락 등)는 모두 500이 됐다.
- 직접 만든 404/405 처리기는 응답 본문만 만들어서, 표준 처리가 넣어 주던 `Allow` 같은 헤더가 빠졌다.

## 해결

- `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속한다. 표준 예외는 상위 클래스가 상태 코드와 헤더를 정하고, `handleExceptionInternal`에서 본문만 공통 포맷으로 바꾼다. 하나씩 두던 400/404/405 처리기는 지웠다.
- 응답 코드는 상태 코드로 고른다. 404 `RESOURCE_NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`, 415 `UNSUPPORTED_MEDIA_TYPE`, 그 밖의 4xx는 `INVALID_REQUEST`, 5xx는 `INTERNAL_SERVER_ERROR`(error 로그)다.
- 없는 경로에 쓰던 `ENTITY_NOT_FOUND`는 엔티티가 아니라 경로를 못 찾은 경우라 `RESOURCE_NOT_FOUND`로 이름을 바꿨다. 이 코드를 쓰는 곳은 없는 경로 응답뿐이었다.
- `GlobalExceptionHandlerTest`에 415 테스트를, `DirectEntryControllerProfileTest`의 405 테스트에 `Allow: GET` 헤더 확인을 추가했다. 수정 전 코드에서 둘 다 실패하는 것을 확인했다.

## 참고

- [입력 검증 실패가 500으로 응답됨](2026-10-28-validation-error-returns-500.md)
- [시뮬레이션 전용 엔드포인트](../trade-offs/2026-11-02-simulation-only-endpoints.md)
