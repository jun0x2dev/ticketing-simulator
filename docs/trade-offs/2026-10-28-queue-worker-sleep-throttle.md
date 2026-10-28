# Queue Worker TPS 제한: 리스너 스레드 sleep

## 선택지

- 소비 후 리스너 스레드를 `1000/tps`ms 재우는 방식
- 토큰 버킷 등 별도 Rate Limiter(Guava RateLimiter, Resilience4j 등)
- Kafka 설정(`max.poll.records`, 폴링 간격)으로 간접 제어

## 선택

`EntryRequestConsumer`가 메시지 1건 처리 후 `Thread.sleep(1000 / tps)` 한다. TPS는 `queue.worker.tps`(기본 100).

## 이유

- 새 의존성 없이 한 줄로 "처리량을 제한하고 나머지는 Kafka에 쌓아 둔다"는 대기열 완충 구조를 그대로 보여 줄 수 있다. 시뮬레이터의 목적(대기열 적용 전/후 비교)에 충분하다.
- 감수한 점: 리스너 스레드 1개 기준이라 컨슈머 동시성을 늘리면 TPS도 비례해서 늘고, 정수 나눗셈이라 tps가 1000을 넘으면 간격이 0ms가 된다. 또 `max.poll.interval.ms`(기본 5분) 안에 한 번에 받은 배치를 처리해야 하므로 tps를 매우 낮추면 리밸런스가 날 수 있다. 정밀 제어가 필요해지면 토큰 버킷으로 교체한다.
