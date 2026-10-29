# seat

## 정의

특정 이벤트(공연)에 속한 좌석 1석의 상태를 관리하는 도메인. 이벤트 생성 시 `seatCount`만큼 좌석이 만들어지고,
동시 선점 방지는 Redis 분산 락(`seat:lock:{seatId}`)이, 락 통과 이후의 영속 상태는 `Seat.status`(MySQL)가 담당한다.

## 주요 상태/흐름

- 상태: `AVAILABLE` → `HELD` → `CONFIRMED`, `HELD` → `AVAILABLE`(해제)
- `GET /api/v1/events/{eventId}/seats` — 좌석 목록 조회
- `POST /api/v1/seats/{seatId}/hold` — `X-Entry-Token` 헤더 필수(없음/만료/타 이벤트 토큰이면 `ENTRY_TOKEN_INVALID`). Redis `SET NX PX`(TTL 5분)로 락 획득 성공한 단 1건만 DB를 `HELD`로 변경. 실패 시 `SEAT_ALREADY_HELD`
- `POST /api/v1/seats/{seatId}/release` — `HELD`를 `AVAILABLE`로 되돌리고 락 강제 해제
- 락 해제는 UUID 토큰이 일치할 때만 수행(TTL 만료 후 타 요청이 얻은 락 보호)
- 선점 시 `heldAt`을 기록한다. `SeatHoldExpirationScheduler`가 5초마다(fixedDelay) `heldAt < now - 5분`인 `HELD` 좌석을 행 잠금(`FOR UPDATE`)으로 조회해 `AVAILABLE`로 복구하고 `seat.released`(`TTL_EXPIRED`)를 발행한다 (Redis 락은 TTL로 이미 만료된 상태)
- 결제 승인 시 payment 도메인이 `confirmSeat`으로 `CONFIRMED` 전환, 거절 시 `releaseSeat` 호출
- 좌석 상태가 바뀌면(선점/확정/해제/TTL 복구) 커밋 이후 `/topic/events/{eventId}/seats`로 `{eventId, seatId, status}`를 WebSocket 브로드캐스트한다 (`SeatStatusBroadcaster`)
- WebSocket 접속: STOMP endpoint `/ws`, 구독 prefix `/topic`

## 관련 이벤트 (Kafka 토픽)

- `seat.released`(`TTL_EXPIRED`): `SeatHoldExpirationScheduler`가 TTL 만료 복구 후 발행한다.
- `seat.confirmed`, `seat.released`(`PAYMENT_FAILED`): payment 도메인이 결제 결과에 따라 발행한다.
- 소비: stats 도메인의 `EventStatsConsumer`가 회차 통계로 집계한다.
