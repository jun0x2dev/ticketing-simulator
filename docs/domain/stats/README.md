# stats

## 정의

Kafka 이벤트를 구독해 회차(event)별 예매 통계를 집계하고 조회하는 도메인. DB 없이 Redis 카운터만 쓴다.

## 주요 상태/흐름

- `EventStatsConsumer`(group `ticketing-stats`)가 `entry.granted`, `seat.confirmed`, `seat.released`를 구독한다.
- 항목: `granted`(입장 허용), `confirmed`(결제 완료), `paymentFailed`(결제 실패 해제), `ttlExpired`(시간 초과 해제). Redis Hash `stats:event:{eventId}`.
- 멱등성: 메시지마다 고유 키(`granted:{requestId}`, `confirmed:{paymentId}`, `released:payment:{paymentId}`, `released:ttl:{seatId}:{occurredAt}`)를 `stats:dedup:{key}`로 남기고, 중복 확인(SET NX)과 증가(HINCRBY)를 Lua 스크립트 하나로 원자적으로 처리한다.
- `seat.*`는 outbox 발행기가 "적어도 한 번" 보내므로 같은 메시지가 다시 올 수 있다. 저장된 payload를 그대로 다시 보내 멱등 키가 같으므로 한 번만 집계된다.
- 통계와 중복 확인 키는 2일 뒤 만료된다(회차는 24시간 뒤 정리).
- `GET /api/v1/events/{eventId}/stats` — 회차 통계. 예매 완료 화면이 2초마다 조회한다.
- 결제 이벤트 DTO는 기본 역직렬화 허용 패키지(queue.dto) 밖이라, 통계 리스너에서만 `spring.json.trusted.packages`를 넓힌다.

## 관련 이벤트 (Kafka 토픽)

| 토픽 | 역할 |
|---|---|
| `entry.granted` | `granted` 집계 |
| `seat.confirmed` | `confirmed` 집계 |
| `seat.released` | 사유에 따라 `paymentFailed` / `ttlExpired` 집계 |

처리 실패 시 공통 에러 핸들러가 재시도 후 각 토픽의 `.DLT`로 보낸다.

| 토픽 | 역할 |
|---|---|
| `entry.granted.DLT`, `seat.confirmed.DLT`, `seat.released.DLT` | `EventStatsDltConsumer`(그룹 `ticketing-stats-dlt`)가 메시지 시각 5초 뒤(`stats.dlt.retry-delay-ms`) 같은 기록 메서드로 다시 집계 |

- 멱등 키 덕분에 이미 집계된 메시지를 다시 넣어도 두 번 세지 않는다. `entry.granted.DLT`에는 WebSocket 릴레이 실패로 온 메시지도 섞이는데, 이 경우 재집계는 "이미 집계된 메시지"로 끝난다.
- 재집계도 실패하면 공통 에러 핸들러가 3회 재시도한 뒤, DLT를 다시 DLT로 보내지 않고 topic/partition/offset과 함께 error 로그를 남긴다. 원본은 DLT에 남아 있어 그 오프셋으로 찾아 수동 처리할 수 있다.
- 종료 중 대기가 끊기면 `InterruptedException`으로 끝내 오프셋을 커밋하지 않는다. 재시작 후 같은 메시지를 다시 받는다(입장 요청 DLT Consumer도 같다).
- WebSocket 릴레이 실패는 재처리하지 않는다. 대기 화면이 1초마다 상태를 폴링하므로 알림이 빠져도 입장은 늦어야 1초 안에 반영되고, 릴레이 Consumer는 인스턴스마다 고유 그룹이라 DLT를 한 곳에서 다시 보내도 그 사용자가 붙은 인스턴스에 닿는다는 보장이 없다.
