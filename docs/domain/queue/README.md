# queue

## 정의

대규모 입장 요청을 Kafka로 완충하고, 일정 처리량(TPS)으로만 입장을 허용해 Redis 입장 토큰을 발급하는 대기열 도메인.
DB entity 없이 producer/consumer, 서비스, Redis 토큰 관리자로 구성된다.

## 주요 상태/흐름

- `POST /api/v1/events/{eventId}/entries` — 입장 요청 접수(202). 이벤트 존재를 확인하고 대기 줄에 세운 뒤 `entry.requested`에 발행하고 `requestId`를 반환한다. 접수 지연을 늘리지 않도록 브로커 응답은 기다리지 않는다. 발행이 실패하면 콜백에서 줄에서 빼고 `entry:failed:{requestId}`로 표시해, 상태 조회가 `FAILED`를 반환하고 대기 화면이 "다시 줄 서기"를 안내한다 ([트러블슈팅](../../troubleshooting/2026-10-29-entry-request-stuck-on-publish-failure.md)).
- `EntryRequestConsumer`가 `queue.worker.tps`(기본 100)에 맞춰 토픽을 소비한다. 처리 후 `1000/tps`ms 대기하므로 못 처리한 요청은 Kafka에 쌓인다.
- `EntryService.grantEntry`: 입장 토큰을 발급하고 `entry.granted`를 발행한다. 토큰 값은 이벤트에 싣지 않는다.
- `POST /api/v1/events/{eventId}/entries/direct` — 대기열을 거치지 않고 즉시 토큰 발급. 부하 비교의 "대기열 적용 전" 기준선 전용이다.
- `GET /api/v1/events/{eventId}/entries/{requestId}` — `WAITING`(대기 순번 `position`, 내 뒤 인원 `behind`, 예상 대기 시간 `estimatedSeconds` 포함) 또는 `GRANTED`(+토큰). 순번이 없는 `WAITING`은 줄에서 막 빠져 입장 처리 중이거나 모르는 요청이다.
- 대기 순번: 입장 요청 시 Kafka 발행 **전에** Redis Sorted Set `queue:line:{eventId}`(score = `queue:seq:{eventId}` INCR 순번)에 줄을 세우고, Queue Worker가 입장을 허용하면 줄에서 뺀다. 순번은 `ZRANK + 1`, 뒤 인원은 `ZCARD - 순번`, 예상 대기 시간은 `ceil(순번 / queue.worker.tps)`. 줄 키는 마지막 접수 후 1시간 뒤 만료된다.
- 토큰은 Redis 키 두 개로 관리한다. `entry:grant:{requestId}` → 토큰(요청당 1회 발급 = 멱등성 키), `entry:token:{token}` → eventId(선점 시 검증용). 둘 다 TTL `queue.token-ttl-seconds`(기본 600초).
- 좌석 선점(`seat` 도메인)은 `X-Entry-Token` 헤더의 토큰이 유효하고 좌석의 이벤트와 같아야 한다.
- 실패 처리: Consumer가 실패하면 1초 간격 3회 재시도 후 `entry.requested.DLT`로 이동한다(`KafkaConsumerConfig`).
- DLT 재처리: `EntryRequestDltConsumer`가 DLT 도착 5초 뒤(`queue.dlt.redrive-delay-ms`) `redriveCount`를 1 올려 원래 토픽으로 한 번 다시 보낸다. 재투입본도 실패해 다시 DLT에 오면 대기 줄에서 빼고 `entry:failed:{requestId}`(토큰과 같은 TTL)로 표시한다. 상태 조회는 `FAILED`를 반환하고, 대기 화면은 "다시 줄 서기"를 안내한다. 재투입 발행 자체가 실패해도 같은 최종 실패로 처리한다. DLT Consumer 자체의 예외는 다시 DLT로 보내지 않고 에러 로그로 남긴다.
- `EntryGrantedRelayConsumer`가 `entry.granted`를 구독해 `/topic/entries/{requestId}`로 WebSocket 알림을 보낸다(토큰 미포함). 클라이언트는 알림을 받으면 `GET /api/v1/events/{eventId}/entries/{requestId}`로 토큰을 가져간다. 인스턴스별 고유 그룹 + 최신 오프셋으로 구독한다.
- 미구현: 입장 요청의 이벤트 오픈 시각 검증, 하위 Consumer DLT(`*.DLT`)의 재처리

## 관련 이벤트 (Kafka 토픽)

| 토픽 | Producer | Consumer | 설명 |
|---|---|---|---|
| `entry.requested` | queue.producer (EntryRequestProducer) | queue.consumer (EntryRequestConsumer) | 입장 요청 적재. 파티션 키 eventId |
| `entry.requested.DLT` | Spring Kafka 에러 핸들러 | queue.consumer (EntryRequestDltConsumer) | 재시도 소진 메시지. 5초 뒤 1회 재투입, 그래도 실패하면 최종 실패 처리 |
| `entry.granted` | queue.producer (EntryGrantedProducer) | queue.consumer (EntryGrantedRelayConsumer), stats.consumer (EventStatsConsumer) | 입장 허용 알림을 WebSocket으로 릴레이 + 통계 집계. 토큰 미포함 |
| `seat.confirmed` | outbox (OutboxRelayScheduler, 결제에서 기록) | stats.consumer (EventStatsConsumer) | 결제 완료 좌석 |
| `seat.released` | outbox (OutboxRelayScheduler, 결제/TTL 복구에서 기록) | stats.consumer (EventStatsConsumer) | 결제 실패(`PAYMENT_FAILED`) 또는 TTL 만료(`TTL_EXPIRED`)로 해제된 좌석 |
| `entry.granted.DLT`, `seat.confirmed.DLT`, `seat.released.DLT` | Spring Kafka 에러 핸들러 | (없음) | 하위 Consumer(통계/릴레이) 재시도 소진 메시지 보관 |

토픽 정의는 `global.config.KafkaTopicConfig`에서 관리한다.
