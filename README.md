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

## 현재 상태

초기 스캐폴딩 단계. 기능 구현 전.
