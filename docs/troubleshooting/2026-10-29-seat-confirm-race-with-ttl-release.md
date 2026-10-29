# 결제 확정이 TTL 복구·동시 결제와 겹치면 상태를 덮어씀

## 증상

- 같은 좌석에 결제 요청 10건을 동시에 보내면 결제 SUCCESS가 여러 건 저장되고 `seat.confirmed`도 여러 번 발행된다. 재현은 `PaymentFlowIntegrationTest.pay_concurrentRequests_confirmedOnlyOnce`에서 잠금을 빼고 돌리면 된다.
- 코드를 따라가 보면 같은 원인으로 다음 일도 생길 수 있다. 결제 승인 직후 TTL 스케줄러가 좌석을 AVAILABLE로 복구해도, 결제 쪽이 그 위에 CONFIRMED를 덮어쓴다. 그러면 `seat.released(TTL_EXPIRED)`와 `seat.confirmed`가 둘 다 나간다.

## 원인

- 행 잠금(`FOR UPDATE`)은 TTL 스케줄러의 `findExpiredHeldSeatsForUpdate`에만 있었다. `confirmSeat`/`releaseSeat`는 잠금 없이 `findById`로 읽었다.
- 잠금이 한쪽에만 있으면 서로를 막지 못한다. 결제 쪽은 HELD를 읽고 엔티티 검증을 통과한 뒤 UPDATE를 보낸다. 이 UPDATE는 id만으로 행을 고르기 때문에, 그 사이 다른 트랜잭션이 상태를 바꿔도 그대로 덮어쓴다.
- 결제 게이트웨이는 트랜잭션 밖에서 호출한다(지연 동안 커넥션을 잡지 않기 위해). 그래서 상태 확인(`getSeat`)과 확정 사이 간격이 길다.

## 해결

- `SeatRepository.findByIdForUpdate`(PESSIMISTIC_WRITE)를 추가했다. `confirmSeat`/`releaseSeat`가 이것으로 읽는다. 이제 TTL 복구, 다른 결제, 해제 중 먼저 잠근 쪽이 커밋한 뒤 최신 상태를 보고 판단한다. 늦은 쪽은 `SEAT_NOT_HELD`로 실패한다.
- 잠금 뒤에 선점자(`heldBy`)도 다시 확인한다. TTL이 지나 다른 사용자가 다시 잡은 좌석을 원래 사용자의 늦은 결제가 확정하지 못하게 하기 위해서다([선점자 확인과 토큰당 1석](../trade-offs/2026-10-29-seat-holder-token-limit.md)).
- 동시 결제 테스트는 잠금이 없으면 실패하고, 잠금이 있으면 통과하는 것을 확인했다.
