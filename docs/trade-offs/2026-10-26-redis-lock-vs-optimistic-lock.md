# 좌석 선점 동시성 제어: Redis 분산 락 선택

## 선택지

- DB 비관적 락(`SELECT ... FOR UPDATE`)
- JPA 낙관적 락(`@Version`) + 재시도
- Redis 분산 락(`SET NX PX`)

## 선택

Redis `SET key token NX PX ttl` 단일 명령으로 락을 잡고, 통과한 요청만 DB 상태를 `HELD`로 변경한다.
`Seat.hold()`에서도 상태를 검증해 이중 방어선을 둔다. 해제는 토큰 비교 후 삭제.

## 이유

- 프로젝트 목표(DESIGN.md §3.2)가 Redis 락 기반 동시성 제어를 증명하는 것이고, 결제 TTL을 락 TTL로 그대로 표현할 수 있다.
- 오픈 순간 같은 좌석에 요청이 몰리면 DB 락/낙관적 락은 실패한 요청까지 DB 커넥션을 소비한다. Redis에서 먼저 걸러내면 DB 부하가 줄어든다.
- 감수한 점: Redis와 DB 두 저장소 간 일관성을 직접 관리해야 한다. 락 TTL이 만료돼도 DB `HELD`는 자동 복구되지 않으므로 별도 복구 장치(스케줄러 등)가 필요하다. 해제 시 get→delete가 비원자적이라 Lua 스크립트로 개선 여지가 있다.
