# event

## 정의

티케팅 대상이 되는 공연/행사 정보를 관리하는 도메인. 이름, 전체 좌석 수, 오픈 시각을 가진다.
좌석(seat) 도메인은 이 Event를 기준으로 좌석을 생성하며, simulation 도메인이 이 Event를 대상으로 부하 시뮬레이션을 실행한다.

## 주요 상태/흐름

- Event는 생성 이후 별도 상태 전이가 없다 (단순 메타데이터).
- `POST /api/v1/events` — 이벤트 생성
- `GET /api/v1/events/{eventId}` — 이벤트 조회

## 관련 이벤트 (Kafka 토픽)

Event 도메인 자체는 Kafka 이벤트를 발행하지 않는다. 다만 `EventController`에 임시로 추가된
`POST /api/v1/events/{eventId}/entry-requests`가 `entry.requested` 토픽(queue 도메인 소유)으로
입장 요청 1건을 수동 발행한다 — load-generator 구현 전까지 Kafka 연동 확인용 테스트 엔드포인트다.
simulation 도메인이 구현되면 이 테스트 엔드포인트는 제거하고 load-generator가 대량으로 발행하는 구조로 대체할 예정.
