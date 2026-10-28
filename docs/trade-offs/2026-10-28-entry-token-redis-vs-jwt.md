# 입장 토큰: Redis 토큰 vs JWT

## 선택지

- JWT(서명된 자체 검증 토큰)
- Redis에 저장하는 불투명(opaque) 랜덤 토큰

## 선택

UUID 토큰을 Redis에 TTL과 함께 저장한다(`entry:token:{token}` → eventId, `entry:grant:{requestId}` → 토큰).

## 이유

- 이미 좌석 락에 Redis를 쓰고 있어 추가 인프라가 없고, TTL 만료가 곧 토큰 만료다. 폐기·재발급도 키 삭제 한 번이다.
- `entry:grant:{requestId}`를 `SET NX`로 선점하는 것이 그대로 Kafka 중복 소비 방지(멱등성 키)가 된다.
- 감수한 점: 선점 요청마다 Redis 조회가 1회 필요하다(JWT는 서명 검증만으로 끝). 다만 좌석 락도 Redis를 거치므로 추가 비용이 작고, 토큰이 없는 요청은 락/DB 전에 걸러진다.
- 사용자 인증이 없는 시뮬레이터라 토큰은 requestId를 아는 사람이 `GET /entries/{requestId}`로 조회할 수 있다(requestId가 UUID인 것에 의존). 실제 서비스라면 사용자 인증과 묶어야 한다.
