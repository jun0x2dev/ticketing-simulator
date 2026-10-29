# 좌석 이벤트 발행에 Transactional Outbox 적용

## 선택지

발행 방식
1. 커밋 후 직접 발행 (기존): DB 트랜잭션을 커밋한 뒤 `KafkaTemplate.send()`
2. Kafka 트랜잭션 + DB 트랜잭션 동기화 (`KafkaTransactionManager`, ChainedTransactionManager 계열)
3. Transactional Outbox + 폴링 발행기 (DB 테이블에 기록 → 스케줄러가 발행)
4. Transactional Outbox + CDC (Debezium이 binlog를 읽어 발행)

발행기의 동시 실행 제어
1. 행 잠금 `FOR UPDATE SKIP LOCKED`
2. 리더 선출/분산 락(ShedLock 등)으로 한 인스턴스만 발행
3. 잠금 없이 모든 인스턴스가 발행 (중복은 Consumer 멱등성에 맡김)

대상 토픽
1. 모든 토픽
2. DB 변경과 함께 나가는 `seat.confirmed`, `seat.released`만

## 선택

- 발행 방식 3번: `outbox_events` 테이블 + 1초 주기 폴링 발행기
- 동시 실행 제어 1번: `FOR UPDATE SKIP LOCKED`로 인스턴스끼리 행을 나눠 가진다
- 대상 2번: 좌석 이벤트만

## 이유

- 1번(커밋 후 직접 발행)은 커밋과 발행 사이에 서버가 죽거나 브로커가 응답하지 않으면 이벤트가 사라진다. 결제가 끝난 좌석이 통계에 영원히 빠지는데, 다시 보낼 근거도 남지 않는다.
- 2번(Kafka 트랜잭션)은 DB와 Kafka를 진짜 원자적으로 묶지 못한다(최선 노력 1PC). DB 커밋 후 Kafka 커밋이 실패하는 틈이 남고, Consumer도 `read_committed`로 바꿔야 한다.
- 4번(CDC)은 Kafka Connect, Debezium 운영이 따로 필요하다. 이 규모와 MVP 범위(모놀리식)에 비해 과하다.
- 3번은 "DB에 기록되면 언젠가 반드시 발행"을 테이블 하나와 스케줄러 하나로 보장한다. 결제는 이미 게이트웨이 호출 뒤에 짧은 트랜잭션(`TransactionTemplate`)이 있고, TTL 복구도 트랜잭션 안이라 행 하나만 더 쓰면 된다.
- 발행은 "적어도 한 번"이라 중복이 생길 수 있다(브로커 응답 전에 대기 시간이 끝난 건). 통계 Consumer가 이미 `paymentId`, `seatId + occurredAt`으로 멱등 집계하고, outbox는 저장해 둔 payload를 그대로 다시 보내므로 멱등 키도 바뀌지 않는다.
- `SKIP LOCKED`는 MySQL 8 기본 기능이라 의존성이 늘지 않는다. 인스턴스가 늘면 처리량도 같이 늘고, 한 인스턴스가 죽어도 잠금이 풀리면 다른 인스턴스가 이어받는다. 리더 선출(2번)은 라이브러리가 하나 늘고 한 대만 일하게 된다. 잠금 없이(3번) 두면 인스턴스 수만큼 중복 발행된다.
- `entry.requested`, `entry.granted`는 DB가 아니라 Redis 작업(대기 줄, 토큰 발급)과 묶여 있어 DB outbox로는 원자성을 만들 수 없다. 대신 각각 다른 장치로 유실에 대비한다.
  - `entry.requested`: 발행 실패 콜백에서 줄(ZSET)에서 빼고 최종 실패로 표시해, 대기 화면이 "다시 줄 서기"를 안내한다. DLT 재투입 발행이 실패해도 같다. 접수 응답 전에 브로커 확인을 기다리는 방식은 이 PC 기준 동시 50건에서 p50이 15ms → 220ms로 늘어나서 택하지 않았다([트러블슈팅](../troubleshooting/2026-10-29-entry-request-stuck-on-publish-failure.md)).
  - `entry.granted`: 토큰이 Redis에 먼저 발급되므로, 알림이 사라져도 대기 화면의 1초 폴링이 토큰을 가져간다. 영향은 통계의 입장 허용 수가 덜 세어지는 것에 그친다.
- 감수한 점:
  - 발행이 커밋 직후가 아니라 최대 약 1초 늦다. 좌석 화면 갱신은 Kafka가 아니라 커밋 후 WebSocket 브로드캐스트라 영향이 없고, 통계만 약간 늦게 반영된다.
  - 발행기는 행 잠금과 DB 커넥션을 잡은 채 브로커 응답을 기다린다. 대기 시간을 5초로 제한하고 `max.block.ms=5000`을 둬서 커넥션 점유를 묶음당 최대 약 10초로 제한했다.
  - 스케줄러 스레드를 다른 작업과 나눠 쓰면 발행이 막힐 때 TTL 복구나 STOMP 하트비트도 밀린다. 그래서 `@Scheduled` 전용 `taskScheduler`(풀 4개)를 따로 두었다([트러블슈팅](../troubleshooting/2026-10-29-scheduled-tasks-on-stomp-scheduler.md)).
  - 계속 실패하는 행(예: DTO 클래스 이름 변경)은 묶음 맨 앞에 남아 매 주기 실패한다. 같은 묶음의 다른 이벤트 발행은 막지 않지만, `attempts`와 error 로그로 찾아 직접 처리해야 한다.
