# event

## 정의

티케팅 대상이 되는 공연/행사 정보를 관리하는 도메인. 이름, 전체 좌석 수, 오픈 시각을 가진다.
좌석(seat) 도메인은 이 Event를 기준으로 좌석을 생성한다. 화면에서는 concert 도메인이 공연 템플릿으로 Event(회차)를 만들고, k6 부하 시나리오는 이 Event에 가상 관객을 보낸다.

## 주요 상태/흐름

- Event는 생성 이후 별도 상태 전이가 없다 (단순 메타데이터). 사용자가 공연을 고를 때마다 생기는 "회차" 단위다.
- 지난 회차 정리: `EventRunCleanupScheduler`가 매시 정각(`event.run-cleanup-cron`)에 오픈 시각이 24시간(`event.run-retention-hours`) 넘게 지난 회차를 100개씩 묶어 좌석 → 회차 순으로 삭제한다. 결제 기록(payments)은 이력으로 남긴다. `events.open_at`에 인덱스를 둔다.
- `POST /api/v1/events` — 이벤트 직접 생성 (k6 시나리오용, 좌석 최대 500석)
- `GET /api/v1/events/{eventId}` — 이벤트 조회

## 관련 이벤트 (Kafka 토픽)

Event 도메인 자체는 Kafka 이벤트를 발행하지 않는다. 입장 요청(`entry.requested`)은 queue 도메인이
전담하며, 대량 입장 요청은 `load-generator`의 k6 스크립트가 입장 요청 API를 호출해 만든다.

> 과거에 `EventController`에 Kafka 연동 확인용 테스트 엔드포인트
> (`POST /api/v1/events/{eventId}/entry-requests`)를 임시로 추가했었는데, 인증 없이 운영 코드에
> 노출된 디버그용 엔드포인트라는 보안 리뷰 피드백에 따라 제거했다. 대신
> `EntryRequestKafkaIntegrationTest`(EmbeddedKafka 기반 통합 테스트)로 대체했다.
