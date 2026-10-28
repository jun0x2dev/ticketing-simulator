# WebSocket 브로드캐스트 방식과 TTL 복구 쿼리 변경

## 선택지

- 좌석 변경 알림: (a) 모든 변경을 Kafka 토픽으로 발행하고 릴레이 Consumer가 WebSocket으로 전달 vs (b) 서비스가 Spring 애플리케이션 이벤트를 발행하고 커밋 이후 리스너가 직접 전송
- TTL 복구 쿼리: 벌크 `UPDATE` vs 행 잠금(`FOR UPDATE`) 조회 후 엔티티 상태 변경

## 선택

- 입장 허용은 DESIGN대로 `entry.granted` → 릴레이 Consumer → WebSocket. 좌석 변경은 (b): `SeatStatusChangedEvent`를 `@TransactionalEventListener(AFTER_COMMIT)`가 받아 `/topic/events/{eventId}/seats`로 전송한다. 선점(HELD)용 Kafka 토픽은 새로 만들지 않는다.
- TTL 복구는 `PESSIMISTIC_WRITE`로 만료 좌석을 조회한 뒤 `Seat.release()`로 되돌린다. 복구된 좌석마다 `seat.released`(TTL_EXPIRED)를 커밋 후 발행한다.

## 이유

- DESIGN의 Kafka 토픽에는 선점 이벤트가 없다. 화면에 "선점됨"을 보이려고 토픽을 늘리는 것보다, 같은 JVM 안의 이벤트로 처리하는 편이 모놀리식 MVP에 맞고 지연도 작다. 서비스가 분리되면 그때 Kafka로 올린다.
- AFTER_COMMIT으로 롤백된 변경이 화면에 나가거나, 클라이언트가 커밋 전 상태를 읽는 문제를 막는다. 감수한 점: 커밋 후 전송이 실패하면 알림이 유실된다(화면은 다음 조회/재접속 때 바로잡힘).
- 벌크 UPDATE는 복구된 좌석 ID를 알 수 없어 브로드캐스트/이벤트를 만들 수 없었다. 행 잠금 조회는 조회~복구 사이에 결제 확정이 끼어들어도 확정된 좌석을 되돌리지 않고, 엔티티 로직(`release()`)을 그대로 쓴다. 감수한 점: 만료 좌석이 많으면 행 수만큼 UPDATE가 나간다.
- 릴레이 Consumer는 인스턴스별 고유 그룹 + `auto.offset.reset=latest`로 구독한다. 같은 그룹이면 한 인스턴스만 받아서 다른 인스턴스의 접속자가 알림을 못 받고, 재시작 때 과거 입장 알림을 다시 흘리지 않기 위해서다.
- STOMP 허용 Origin은 로컬 개발 편의상 `*`이다. 배포 시 제한해야 한다.
