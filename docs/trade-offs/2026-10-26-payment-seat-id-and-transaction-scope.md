# 결제 요청 식별자와 트랜잭션 범위

## 선택지

- 결제 대상 식별: DESIGN.md 초안의 `seatHoldId`(별도 선점 엔티티) vs `seatId`
- 결제 처리 트랜잭션: `pay()` 전체를 하나의 트랜잭션 vs 게이트웨이 호출을 트랜잭션 밖에 두고 단계별 짧은 트랜잭션

## 선택

- `POST /api/v1/payments`는 `seatId`를 받는다 (DESIGN.md 초안의 `seatHoldId`에서 변경).
- `pay()`는 `Propagation.NOT_SUPPORTED`로 트랜잭션 없이 실행하고, 게이트웨이 호출 뒤의 좌석 확정/해제와 결제 기록 저장만 `TransactionTemplate`으로 한 트랜잭션에 묶는다.
- (2026-10-29 변경) 처음에는 좌석 확정/해제와 결제 기록 저장이 각각 자체 트랜잭션이었다. 기록 저장이 실패하면 기록 없이 확정된 좌석이 남고 `seat.confirmed`도 나가지 않아, 둘을 한 트랜잭션으로 묶었다. [트러블슈팅](../troubleshooting/2026-10-29-payment-deadline-and-partial-commit.md)

## 이유

- 선점은 `Seat.status = HELD` + Redis 락으로 이미 표현되고 좌석당 동시에 하나만 존재한다. 별도 hold 엔티티를 만들면 같은 정보를 이중 관리하게 되고 MVP 목표(동시성/이벤트)와 무관한 복잡도만 늘어난다.
- Mock 게이트웨이의 지연(최대 1.5초) 동안 DB 커넥션을 잡고 있으면 오픈 직후 트래픽에서 커넥션 풀이 고갈된다.
- 감수한 점: 결제 승인 후 `confirmSeat` 전에 TTL 스케줄러가 좌석을 해제하면 결제는 승인됐으나 확정은 실패한다. (2026-10-29) 이 경우 게이트웨이에 승인 취소를 요청하고 `CANCELLED`로 기록하도록 바꿨다([트러블슈팅](../troubleshooting/2026-10-29-duplicate-payment-approval.md)). 또 DB 커밋 후 Kafka 발행이 실패하면 이벤트가 유실될 수 있었다. (2026-10-29) 이 부분은 [Transactional Outbox](./2026-10-29-transactional-outbox.md)로 해결했다.
