# queue

## 정의

대규모 입장 요청을 Kafka로 완충하고, 일정 처리량(TPS)으로만 입장을 허용해 Redis 입장 토큰을 발급하는 대기열 도메인.
DB entity 없이 producer/consumer, 서비스, Redis 토큰 관리자로 구성된다.

## 주요 상태/흐름

- `POST /api/v1/events/{eventId}/entries` — 입장 요청 접수(202). 이벤트 존재를 확인한 뒤 `entry.requested`에 발행하고 `requestId`를 반환한다.
- `EntryRequestConsumer`가 `queue.worker.tps`(기본 100)에 맞춰 토픽을 소비한다. 처리 후 `1000/tps`ms 대기하므로 못 처리한 요청은 Kafka에 쌓인다.
- `EntryService.grantEntry`: 입장 토큰을 발급하고 `entry.granted`를 발행한다. 토큰 값은 이벤트에 싣지 않는다.
- `GET /api/v1/entries/{requestId}` — `WAITING`(토큰 없음, 존재하지 않는 requestId 포함) 또는 `GRANTED`(+토큰).
- 토큰은 Redis 키 두 개로 관리한다. `entry:grant:{requestId}` → 토큰(요청당 1회 발급 = 멱등성 키), `entry:token:{token}` → eventId(선점 시 검증용). 둘 다 TTL `queue.token-ttl-seconds`(기본 600초).
- 좌석 선점(`seat` 도메인)은 `X-Entry-Token` 헤더의 토큰이 유효하고 좌석의 이벤트와 같아야 한다.
- 실패 처리: Consumer가 실패하면 1초 간격 3회 재시도 후 `entry.requested.DLT`로 이동한다(`KafkaConsumerConfig`).
- 미구현: WebSocket 대기 순번 브로드캐스트, DLT 소비/재처리, 입장 요청의 이벤트 오픈 시각 검증

## 관련 이벤트 (Kafka 토픽)

| 토픽 | Producer | Consumer | 설명 |
|---|---|---|---|
| `entry.requested` | queue.producer (EntryRequestProducer) | queue.consumer (EntryRequestConsumer) | 입장 요청 적재. 파티션 키 eventId |
| `entry.requested.DLT` | Spring Kafka 에러 핸들러 | (미구현) | 재시도 소진 메시지 |
| `entry.granted` | queue.producer (EntryGrantedProducer) | (미구현) | 입장 허용 알림(WebSocket 릴레이 예정). 토큰 미포함 |
| `seat.confirmed` | payment.producer (SeatEventProducer) | (미구현) | 결제 완료 좌석 |
| `seat.released` | payment.producer (SeatEventProducer) | (미구현) | 결제 실패 좌석. TTL 만료 해제 시 발행은 미구현 |

토픽 정의는 `global.config.KafkaTopicConfig`에서 관리한다.
