# outbox

## 정의

DB 상태 변경과 Kafka 이벤트 발행을 원자적으로 묶는 Transactional Outbox 도메인. 발행할 이벤트를 비즈니스 변경과 같은 트랜잭션에 `outbox_events` 행으로 기록하고, 커밋된 행만 발행기가 Kafka로 보낸다. 커밋되면 이벤트는 반드시 발행되고(적어도 한 번), 롤백되면 이벤트도 남지 않는다.

대상은 DB 변경과 함께 나가는 `seat.confirmed`, `seat.released`다. `entry.requested`, `entry.granted`는 DB가 아니라 Redis 작업과 묶여 있어 대상이 아니다([선택 이유](../../trade-offs/2026-10-29-transactional-outbox.md)).

## 주요 상태/흐름

- 상태: `published_at = null`(발행 대기) → `published_at` 기록(발행 완료) → 24시간 뒤 삭제
- 기록: `OutboxService.append(topic, key, payload)`. `Propagation.MANDATORY`라 호출한 쪽 트랜잭션 안에서만 기록되고, 트랜잭션 밖에서 부르면 `IllegalTransactionStateException`. payload는 Jackson으로 JSON 직렬화하고 DTO 클래스 이름을 `payload_type`에 남긴다.
  - 결제 승인: 좌석 확정 + Payment(SUCCESS) + `seat.confirmed`가 한 트랜잭션
  - 결제 거절: 좌석 해제 + Payment(FAILED) + `seat.released`(`PAYMENT_FAILED`)가 한 트랜잭션
  - TTL 복구: 좌석 복구 + `seat.released`(`TTL_EXPIRED`)가 한 트랜잭션
- 발행: `OutboxRelayScheduler.relay()`가 이전 실행 종료 후 1초(`outbox.relay.fixed-delay-ms`)마다 실행된다.
  1. 발행 대기 행을 id 순으로 최대 100건 `FOR UPDATE SKIP LOCKED`로 가져온다. 서버가 여러 대여도 다른 인스턴스가 잡은 행은 건너뛴다.
  2. JSON을 원래 DTO로 되돌려 `KafkaTemplate`으로 모두 보낸 뒤, 전체 5초(`outbox.relay.send-timeout-ms`) 안에서 브로커 응답을 기다린다. 객체로 보내므로 Consumer가 받는 타입 헤더는 직접 발행하던 때와 같다.
  3. 응답을 받은 건은 `published_at`을 기록하고, 실패/무응답 건은 `attempts`만 올리고 대기로 남긴다. 실패가 있으면 주기당 한 번 error 로그를 남긴다.
  4. 100건을 모두 발행했으면 밀린 이벤트가 더 있을 수 있어 바로 이어서 발행한다. 실패가 섞이면 다음 주기를 기다린다(브로커 장애 중 헛도는 것 방지).
- 정리: `OutboxRelayScheduler.purgePublished()`가 매시 30분(`outbox.purge-cron`)에 발행 후 24시간(`outbox.retention-hours`)이 지난 행을 1000건씩 지운다. 발행 대기 행은 지우지 않는다.
- 중복: 응답 대기 시간이 끝난 뒤 브로커에 도착한 메시지는 다음 주기에 한 번 더 발행된다. 이 토픽의 Consumer(통계)는 `paymentId`, `seatId + occurredAt` 멱등 키로 한 번만 반영한다. payload는 저장된 그대로 다시 보내므로 `occurredAt`도 바뀌지 않는다.
- 운영 설정(`application.properties`): `spring.task.scheduling.pool.size=4`(발행이 TTL 복구 등 다른 스케줄러를 막지 않게, `SchedulingConfig`의 `taskScheduler` 빈에 적용 — [STOMP 스케줄러와 분리한 이유](../../troubleshooting/2026-10-29-scheduled-tasks-on-stomp-scheduler.md)), `spring.kafka.producer.properties.max.block.ms=5000`(브로커 장애 시 `send()`가 60초 막히지 않게). 테스트는 `outbox.relay.enabled=false`로 발행기를 끄고 직접 호출한다.

## 관련 이벤트 (Kafka 토픽)

| 토픽 | 기록하는 곳 | 소비 |
|---|---|---|
| `seat.confirmed` | `PaymentServiceImpl` (결제 승인) | stats `EventStatsConsumer` |
| `seat.released` | `PaymentServiceImpl` (결제 거절), `SeatServiceImpl.releaseExpiredSeats` (TTL 만료) | stats `EventStatsConsumer` |
