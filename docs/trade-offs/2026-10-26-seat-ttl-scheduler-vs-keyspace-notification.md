# 좌석 TTL 만료 복구: 스케줄러 vs Redis 만료 이벤트

## 선택지

- Redis keyspace notification(`__keyevent@0__:expired`) 구독 후 DB 복구
- DB의 `heldAt` 기준으로 스케줄러(`@Scheduled`)가 주기적으로 일괄 복구

## 선택

`Seat.heldAt`을 기록하고, 5초 간격 스케줄러가 만료된 `HELD` 좌석을 단일 벌크 UPDATE로 `AVAILABLE`로 되돌린다.

## 이유

- keyspace notification은 fire-and-forget(Pub/Sub)이라 구독자 다운·재연결 중 발생한 만료 이벤트가 유실되고, 유실되면 좌석이 영구히 잠긴다. 스케줄러는 다음 주기에 반드시 따라잡는다.
- Redis 설정(`notify-keyspace-events`) 의존이 없고, 판단 기준이 DB 하나라 테스트가 쉽다(기준 시각을 인자로 받음).
- 감수한 점: 복구가 최대 약 5초 지연된다. 이 구간에 재선점 요청이 오면 락은 풀렸지만 DB가 `HELD`라 `SEAT_ALREADY_HELD`로 거절된다(안전한 방향의 실패). 다중 인스턴스에서는 같은 UPDATE가 중복 실행돼도 멱등이라 문제없다. `heldAt` 인덱스는 좌석 수가 커지면 검토한다.
