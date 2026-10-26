# seat

## 정의

특정 이벤트(공연)에 속한 좌석 1석의 상태를 관리하는 도메인. 이벤트 생성 시 `seatCount`만큼 좌석이 만들어지고,
동시 선점 방지는 Redis 분산 락(`seat:lock:{seatId}`)이, 락 통과 이후의 영속 상태는 `Seat.status`(MySQL)가 담당한다.

## 주요 상태/흐름

- 상태: `AVAILABLE` → `HELD` → `CONFIRMED`, `HELD` → `AVAILABLE`(해제)
- `GET /api/v1/events/{eventId}/seats` — 좌석 목록 조회
- `POST /api/v1/seats/{seatId}/hold` — Redis `SET NX PX`(TTL 5분)로 락 획득 성공한 단 1건만 DB를 `HELD`로 변경. 실패 시 `SEAT_ALREADY_HELD`
- `POST /api/v1/seats/{seatId}/release` — `HELD`를 `AVAILABLE`로 되돌리고 락 강제 해제
- 락 해제는 UUID 토큰이 일치할 때만 수행(TTL 만료 후 타 요청이 얻은 락 보호)
- 미구현: TTL 만료 시 DB `HELD` → `AVAILABLE` 자동 복구, 입장 토큰 검증, `confirm()` 호출(결제 연동)

## 관련 이벤트 (Kafka 토픽)

현재 발행/소비 없음. 결제 단계에서 `seat.confirmed`, `seat.released`를 발행할 예정.
