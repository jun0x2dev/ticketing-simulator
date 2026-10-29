# seat

## 정의

특정 이벤트(공연)에 속한 좌석 1석의 상태를 관리하는 도메인. 이벤트 생성 시 `seatCount`만큼 좌석이 만들어지고,
동시 선점 방지는 Redis 분산 락(`seat:lock:{seatId}`)이, 락 통과 이후의 영속 상태는 `Seat.status`(MySQL)가 담당한다.

## 주요 상태/흐름

- 상태: `AVAILABLE` → `HELD` → `CONFIRMED`, `HELD` → `AVAILABLE`(해제)
- `GET /api/v1/events/{eventId}/seats` — 좌석 목록 조회
- `POST /api/v1/seats/{seatId}/hold` — `X-Entry-Token` 헤더 필수(없음/만료/타 이벤트 토큰이면 `ENTRY_TOKEN_INVALID`). Redis `SET NX PX`(TTL 5분)로 락 획득 성공한 단 1건만 DB를 `HELD`로 변경. 실패 시 `SEAT_ALREADY_HELD`
- 선점자: 선점 시 입장 토큰을 `heldBy`에 기록한다. 결제/해제는 같은 토큰으로만 가능하고, 다르면 `SEAT_HOLDER_MISMATCH`(403)
- 제한시간: 결제/해제 시 `heldAt + 5분`이 지났으면 `SEAT_HOLD_EXPIRED`(409)로 거절한다. TTL 스케줄러의 실행 간격과 관계없이 5분이 정확한 마감이 된다
- 토큰당 1석: 좌석 락보다 먼저 `seat:holder:{token}`(값 seatId, TTL 5분)을 `SET NX`로 건다. 이미 있으면 `SEAT_HOLD_LIMIT_EXCEEDED`(409). 좌석 락 실패·DB 반영 실패·확정·해제 시 지우고, TTL 만료 시에는 좌석 락과 함께 스스로 사라진다
- `POST /api/v1/seats/{seatId}/release` — `X-Entry-Token` 헤더 필수. 선점자 본인만 `HELD`를 `AVAILABLE`로 되돌리고 좌석 락과 토큰 표시를 지운다 (결제 화면의 "좌석 다시 선택")
- 락 획득/해제는 공통 `RedisLock`(global.lock)을 쓴다. 해제는 UUID 토큰이 일치할 때만, 조회와 삭제를 Lua 스크립트 하나로 원자적으로 수행한다(TTL 만료 후 타 요청이 얻은 락 보호)
- Redis 정리는 DB 트랜잭션 결과를 따른다: 선점은 커밋되지 않으면(검증 예외든 커밋 실패든) `afterCompletion`에서 좌석 락과 토큰 표시를 풀고, 확정/해제는 `afterCommit`에서만 토큰 표시와 좌석 락을 지운다. DB와 Redis가 어긋나 좌석이나 사용자가 5분 동안 묶이는 일을 막는다([트러블슈팅](../../troubleshooting/2026-10-29-redis-side-effects-before-commit.md))
- 선점 시 `heldAt`을 기록한다. `SeatHoldExpirationScheduler`가 5초마다(fixedDelay) `heldAt < now - 5분`인 `HELD` 좌석을 행 잠금(`FOR UPDATE`)으로 조회해 `AVAILABLE`로 복구하고, 같은 트랜잭션에서 `seat.released`(`TTL_EXPIRED`)를 outbox에 기록한다 (Redis 락은 TTL로 이미 만료된 상태)
- 결제 승인 시 payment 도메인이 `confirmSeat`으로 `CONFIRMED` 전환, 거절 시 `releaseSeat` 호출
- `confirmSeat`/`releaseSeat`도 행 잠금(`findByIdForUpdate`)으로 읽은 뒤 선점자를 확인한다. TTL 복구, 같은 좌석의 동시 결제와 순서가 보장된다 ([트러블슈팅](../../troubleshooting/2026-10-29-seat-confirm-race-with-ttl-release.md))
- 좌석 상태가 바뀌면(선점/확정/해제/TTL 복구) 커밋 이후 `/topic/events/{eventId}/seats`로 `{eventId, seatId, status}`를 WebSocket 브로드캐스트한다 (`SeatStatusBroadcaster`)
- WebSocket 접속: STOMP endpoint `/ws`, 구독 prefix `/topic`

## 관련 이벤트 (Kafka 토픽)

- `seat.released`(`TTL_EXPIRED`): `SeatServiceImpl.releaseExpiredSeats`가 복구 트랜잭션에서 outbox에 기록하고, outbox 발행기가 보낸다.
- `seat.confirmed`, `seat.released`(`PAYMENT_FAILED`): payment 도메인이 결제 결과와 같은 트랜잭션에서 outbox에 기록한다.
- 소비: stats 도메인의 `EventStatsConsumer`가 회차 통계로 집계한다.
