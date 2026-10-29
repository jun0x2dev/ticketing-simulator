# payment

## 정의

선점(HELD)된 좌석에 대한 Mock 결제를 처리하고 결과를 기록하는 도메인. 실제 PG 없이 `MockPaymentGateway`가
임의 지연 후 확률적으로 승인/거절한다(`payment.mock.success-rate` 기본 0.8, 지연 200~1500ms).

## 주요 상태/흐름

- `POST /api/v1/payments` `{ "seatId": 1 }` + `X-Entry-Token` 헤더(선점할 때 쓴 토큰) — 결제 요청. 거절(FAILED)도 `success: true`의 정상 결과로 응답한다.
- 흐름: 좌석이 HELD이고 선점자가 요청자인지 확인 → 게이트웨이 호출(트랜잭션 밖) → 승인이면 `confirmSeat` + Payment(SUCCESS) 저장(한 트랜잭션) 후 `seat.confirmed` 발행, 거절이면 `releaseSeat` + Payment(FAILED) 저장(한 트랜잭션) 후 `seat.released` 발행. 기록 저장이 실패하면 좌석 확정/해제도 롤백된다
- HELD가 아닌 좌석은 `SEAT_NOT_HELD`, 남이 선점한 좌석은 `SEAT_HOLDER_MISMATCH`, 선점 후 5분이 지난 좌석은 `SEAT_HOLD_EXPIRED`로 게이트웨이 호출 전에 거절한다 (TTL 스케줄러가 아직 풀지 않았어도)
- 결제 중 제한시간이 지나면 승인 후 `confirmSeat`에서 `SEAT_HOLD_EXPIRED`로, TTL 스케줄러가 이미 해제했다면 `SEAT_NOT_HELD`로, 그 사이 다른 사람이 다시 잡았다면 `SEAT_HOLDER_MISMATCH`로 실패한다 (Mock이라 환불 처리 없음)
- 같은 좌석에 결제가 동시에 들어와도 `confirmSeat`의 행 잠금 때문에 1건만 확정되고 나머지는 `SEAT_NOT_HELD`로 실패한다
- TTL 만료 해제 시 `seat.released`(`TTL_EXPIRED`, paymentId null)는 seat 스케줄러가 발행한다
- 두 토픽은 stats 도메인의 `EventStatsConsumer`가 회차 통계로 집계한다(paymentId 멱등 키).

## 관련 이벤트 (Kafka 토픽)

| 토픽 | 발행 시점 | 키 | 멱등성 키 |
|---|---|---|---|
| `seat.confirmed` | 결제 승인 | eventId | paymentId |
| `seat.released` | 결제 거절 (`PAYMENT_FAILED`) 또는 TTL 만료 (`TTL_EXPIRED`) | eventId | paymentId (TTL 만료는 null) |
