# 실시간 티케팅 시뮬레이터

특정 시각에 대규모 요청이 몰리는 티케팅 상황을 직접 설정해서 재현하는 시뮬레이터입니다.
대기열(Kafka), 좌석 선점 동시성 제어(Redis), 결제 제한시간(TTL), 실시간 브로드캐스트(WebSocket)를 핵심으로 다룹니다.

## 문서

- 설계: [`DESIGN.md`](./DESIGN.md)
- 작업 규칙: [`CLAUDE.md`](./CLAUDE.md)
- 도메인 정의: [`docs/domain`](./docs/domain)
- 트레이드오프 기록: [`docs/trade-offs`](./docs/trade-offs)
- 트러블슈팅: [`docs/troubleshooting`](./docs/troubleshooting)

## 구성

- `backend` — Spring Boot 3.x / Java 17 API 서버
- `frontend` — Vite + React + TypeScript
- `load-generator` — k6 부하 시나리오 (대기열 적용 전/후)
- `docker-compose.yml` — 로컬 MySQL / Redis / Kafka

## 현재 상태

백엔드 핵심 흐름 구현 완료: 이벤트/좌석 생성 → 입장 요청(Kafka 대기열, TPS 제한) → 입장 토큰(Redis) →
좌석 선점(Redis 락) → Mock 결제 → 좌석 확정/해제, 결제 TTL 만료 자동 해제, WebSocket 실시간 알림.
프론트엔드(공연 목록 → 대기 → 좌석 선택 → 결제 → 예매 완료) 화면 연결 완료. 실행 방법은 [`frontend/README.md`](./frontend/README.md) 참고.

## 부하 테스트: 대기열 적용 전/후

오픈 직후 가상 유저 300명이 동시에 입장해서 좌석 100석을 두고 선점을 시도하는 상황을 k6로 재현했다.

| 지표 | 대기열 적용 전 (`direct.js`) | 대기열 적용 후 (`queue.js`) |
|---|---|---|
| 좌석 선점 요청 p95 | 9.09s | 0.69s |
| 좌석 선점 요청 중앙값 | 6.44s | 6.17ms |
| 입장 요청 p95 | 9.05s | 1.19s |
| 전체 요청 p95 | 9.09s | 1.04s |
| 요청 실패율 | 0% | 0% |
| 대기열 대기 시간 p95 | - | 23.6s |

- **적용 전**: 300명이 토큰을 즉시 받고 동시에 선점을 시도한다. 20개 요청 스레드와 DB 커넥션 5개가 한꺼번에 막혀서 선점 응답이 수 초씩 밀린다. 첫 측정에서는 p95가 12.4s였다.
- **적용 후**: 입장 요청은 Kafka에 쌓이고 Queue Worker가 초당 20명씩만 입장시킨다. 서버에 동시에 들어오는 선점 요청이 줄어서 선점 응답 중앙값이 6ms로 유지된다. 그 대신 사용자는 줄을 서서 기다린다(p95 23.6s).
- 대기열은 서버가 처리할 수 있는 만큼만 들여보내서 "들어온 사용자의 경험"과 서버 안정성을 지키는 구조이고, 기다림은 대기 화면(WebSocket 알림)으로 보여 준다.

**측정 조건**: 한 대의 Windows PC(WSL2)에서 앱(JVM 최대 512MB), Docker의 MySQL 8.4 / Redis 7 / Kafka 3.9(힙 256MB), k6(CPU 1개)를 함께 실행.
작은 서버를 가정해 `server.tomcat.threads.max=20`, `spring.datasource.hikari.maximum-pool-size=5`, `queue.worker.tps=20`으로 제한했다.
같은 PC에서 가상 유저 2,000명으로 대기열 적용 전 시나리오를 돌렸을 때는 요청의 51%가 실패(연결 거부, 60초 타임아웃)했지만, PC 자체가 버티지 못해 적용 후 시나리오는 같은 규모로 측정하지 못했다.
절대 수치보다 같은 조건에서의 전/후 차이를 봐야 한다. 실행 방법은 [`load-generator/README.md`](./load-generator/README.md) 참고.
