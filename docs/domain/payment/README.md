# payment

## 정의

선점(HELD)된 좌석에 대한 Mock 결제를 처리하고 결과를 기록하는 도메인. 실제 PG 없이 `MockPaymentGateway`가
임의 지연 후 확률적으로 승인/거절한다(`payment.mock.success-rate` 기본 0.8, 지연 200~1500ms).

## 주요 상태/흐름

- `POST /api/v1/payments` `{ "seatId": 1 }` + `X-Entry-Token` 헤더(선점할 때 쓴 토큰) — 결제 요청. 거절(FAILED)도 `success: true`의 정상 결과로 응답한다.
- 흐름: 좌석이 HELD이고 선점자가 요청자인지 확인 → 게이트웨이 호출(트랜잭션 밖) → 승인이면 `confirmSeat` + Payment(SUCCESS) 저장 + `seat.confirmed` outbox 기록, 거절이면 `releaseSeat` + Payment(FAILED) 저장 + `seat.released` outbox 기록. 각각 한 트랜잭션이라 하나라도 실패하면 모두 롤백되고, 커밋되면 이벤트는 outbox 발행기가 반드시 보낸다 ([outbox](../outbox/README.md))
- HELD가 아닌 좌석은 `SEAT_NOT_HELD`, 남이 선점한 좌석은 `SEAT_HOLDER_MISMATCH`, 선점 후 5분이 지난 좌석은 `SEAT_HOLD_EXPIRED`로 게이트웨이 호출 전에 거절한다 (TTL 스케줄러가 아직 풀지 않았어도)
- 결제 진행 락: 게이트웨이를 부르기 전에 좌석 단위 Redis 락(`payment:lock:{seatId}`, TTL 30초 `payment.lock-ttl-ms`)을 잡는다. 같은 좌석의 결제가 진행 중이면 `PAYMENT_IN_PROGRESS`(409)로 바로 거절해 게이트웨이 승인이 중복으로 나가지 않는다. 락을 잡은 뒤 선점 상태를 확인하므로, 앞선 결제가 막 확정했다면 `SEAT_NOT_HELD`가 된다. 락은 결제가 끝나면 획득 토큰으로만 해제한다([트러블슈팅](../../troubleshooting/2026-10-29-duplicate-payment-approval.md))
- 승인 후 확정 실패: 결제 중 제한시간이 지나면 `SEAT_HOLD_EXPIRED`, TTL 스케줄러가 이미 해제했다면 `SEAT_NOT_HELD`, 다른 사람이 다시 잡았다면 `SEAT_HOLDER_MISMATCH`로 확정이 실패한다. 이때 게이트웨이에 승인 취소(`cancel(approvalId)`)를 요청하고 Payment(`CANCELLED`, 승인 번호)를 남긴 뒤 원래 예외를 응답한다. 취소나 기록이 실패하면 승인 번호와 함께 error 로그를 남겨 수동 처리한다
- 상태: `SUCCESS`(승인+확정), `FAILED`(거절), `CANCELLED`(승인 후 확정 실패로 승인 취소). `approval_id`는 승인된 결제(SUCCESS, CANCELLED)에만 있다
- 같은 좌석의 확정은 `confirmSeat`의 행 잠금으로도 한 번 더 막는다(결제 락 TTL이 지난 극단적인 경우 대비)
- TTL 만료 해제 시 `seat.released`(`TTL_EXPIRED`, paymentId null)는 seat 도메인이 복구 트랜잭션에서 outbox에 기록한다
- 두 토픽은 stats 도메인의 `EventStatsConsumer`가 회차 통계로 집계한다(paymentId 멱등 키).

## 관련 이벤트 (Kafka 토픽)

| 토픽 | 발행 시점 | 키 | 멱등성 키 |
|---|---|---|---|
| `seat.confirmed` | 결제 승인 | eventId | paymentId |
| `seat.released` | 결제 거절 (`PAYMENT_FAILED`) 또는 TTL 만료 (`TTL_EXPIRED`) | eventId | paymentId (TTL 만료는 null) |
