# payment

## 정의

선점(HELD)된 좌석에 대한 Mock 결제를 처리하고 결과를 기록하는 도메인. 실제 PG 없이 `MockPaymentGateway`가
임의 지연 후 확률적으로 승인/거절한다(`payment.mock.success-rate` 기본 0.8, 지연 200~1500ms).

## 주요 상태/흐름

- `POST /api/v1/payments` `{ "seatId": 1 }` — 결제 요청. 거절(FAILED)도 `success: true`의 정상 결과로 응답한다.
- 흐름: 좌석이 HELD인지 확인 → 게이트웨이 호출(트랜잭션 밖) → 승인이면 `confirmSeat` + Payment(SUCCESS) 저장 + `seat.confirmed` 발행, 거절이면 `releaseSeat` + Payment(FAILED) 저장 + `seat.released` 발행
- HELD가 아닌 좌석은 게이트웨이 호출 전에 `SEAT_NOT_HELD`로 거절한다
- 결제 중 TTL 스케줄러가 좌석을 해제하면 승인 후 `confirmSeat`에서 `SEAT_NOT_HELD`로 실패한다 (Mock이라 환불 처리 없음)
- 미구현: TTL 만료로 해제될 때의 `seat.released` 발행, 두 토픽의 Consumer(통계)

## 관련 이벤트 (Kafka 토픽)

| 토픽 | 발행 시점 | 키 | 멱등성 키 |
|---|---|---|---|
| `seat.confirmed` | 결제 승인 | eventId | paymentId |
| `seat.released` | 결제 거절 (reason: `PAYMENT_FAILED`) | eventId | paymentId |
