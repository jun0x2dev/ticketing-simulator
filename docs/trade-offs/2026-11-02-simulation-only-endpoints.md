# 시뮬레이션 전용 기능(대기열 우회 API, WebSocket Origin)을 막는 방식

## 선택지

- 대기열 우회 API(`entries/direct`)
  - (a) 그대로 두고 문서로만 "배포 시 막기"라고 안내한다.
  - (b) 설정값(`...enabled=false`)이 기본이고 로컬 설정에서 켠다.
  - (c) 컨트롤러를 분리해 `@Profile({"local", "bench"})`로 그 프로필에서만 등록한다.
- STOMP 허용 Origin
  - (a) `*`를 유지한다.
  - (b) 기본은 같은 Origin만 받고, 다른 Origin은 설정(`websocket.allowed-origin-patterns`)으로만 추가한다.

## 선택

- 대기열 우회 API: (c). 별도 `DirectEntryController`를 `local`/`bench` 프로필에서만 등록한다.
- Origin: (b). 기본값은 빈 목록(같은 Origin만 허용)이다.

## 이유

- (a)는 누구나 대기열을 건너뛰고 바로 입장 토큰을 받는 구멍이 배포본에 그대로 남는다. 문서로 하는 안내는 지켜진다는 보장이 없다.
- (b)도 기본값은 안전하다. 하지만 켜는 설정이 git에 올라가지 않는 로컬 설정 파일(`application-local.properties`)과 부하 측정용 설정에 흩어진다. 그러면 저장소를 새로 받은 사람의 `direct.js`가 이유 없이 실패한다. 이 API는 k6 기준선 측정 외에는 쓸 일이 없으므로, 그 측정을 하는 프로필에 묶는 편이 명확하다. 컨트롤러 단위로 빠지므로 운영 환경에는 매핑 자체가 없다.
- 감수한 점: 기본 활성 프로필이 `local`(공통 `application.properties`)이라, 배포할 때 프로필을 바꾸지 않으면 열린다. 배포 프로필이 따로 없는 현재 구조에서 생기는 한계라 README의 남은 과제에 적었다.
- 매핑이 빠지면 `POST .../entries/direct`가 상태 조회 `GET .../entries/{requestId}`와 경로만 겹친다. 이때 나는 예외(`HttpRequestMethodNotSupportedException`)와 없는 경로 예외(`NoResourceFoundException`)를 전역 처리기의 `Exception` 처리가 잡아 500으로 응답하고 있었다. 405(`METHOD_NOT_ALLOWED`)와 404로 따로 처리했다. 이후 Spring MVC 표준 예외 전체를 상위 처리기(`ResponseEntityExceptionHandler`)에 맡기도록 바꿨다([트러블슈팅](../troubleshooting/2026-11-03-standard-mvc-exceptions-returned-as-500.md)).
- WebSocket에는 CORS가 적용되지 않는다. Origin이 `*`이면 다른 사이트의 페이지가 사용자의 브라우저로 `/ws`에 붙어 입장 허용과 좌석 알림을 엿들을 수 있다. 로컬 개발 서버(Vite) 프록시는 `changeOrigin` 없이 Host 헤더를 그대로 넘기므로 같은 Origin으로 판정된다. 그래서 로컬에서도 `*`가 필요 없었다.
