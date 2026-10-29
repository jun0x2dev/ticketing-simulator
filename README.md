# 실시간 티케팅 시뮬레이터

인기 공연 티켓 오픈 순간처럼 요청이 한꺼번에 몰리는 상황을 재현하는 시뮬레이터입니다.
공연을 골라 대기열에 줄을 서고, 다른 관객과 좌석을 다투고, 제한시간 안에 결제하는 과정을 직접 체험할 수 있습니다.
k6 가상 관객을 같은 회차에 보내면 내 화면에서 좌석이 실시간으로 팔려 나갑니다.

대기열(Kafka), 좌석 선점 동시성 제어(Redis), 결제 제한시간(TTL), 실시간 브로드캐스트(WebSocket)를 핵심으로 다룹니다.

## 핵심 구현

| 주제 | 구현 | 기록 |
|---|---|---|
| 대기열 완충 | 입장 요청을 Kafka `entry.requested`에 쌓고, Queue Worker가 설정한 처리량(TPS)으로만 입장시킨다 | [TPS 제한](./docs/trade-offs/2026-10-28-queue-worker-sleep-throttle.md) |
| 대기 순번 | 접수 순서를 Redis Sorted Set에 따로 기록해 `ZRANK`로 순번, 뒤 인원, 예상 대기 시간을 계산한다 | [순번 계산](./docs/trade-offs/2026-10-29-waiting-position-redis-zset.md) |
| 입장 토큰 | 입장이 허용되면 Redis에 TTL 토큰을 발급한다. requestId 단위 `SET NX`라 중복 소비에도 한 번만 발급된다 | [Redis 토큰 vs JWT](./docs/trade-offs/2026-10-28-entry-token-redis-vs-jwt.md) |
| 좌석 동시성 | 좌석 단위 Redis 락(`SET NX PX`)으로 동시 선점 중 1건만 통과시키고, 엔티티 상태 검증으로 이중 방어한다 | [Redis 락 선택](./docs/trade-offs/2026-10-26-redis-lock-vs-optimistic-lock.md) |
| 결제 TTL | 결제 없이 5분이 지난 좌석을 스케줄러가 행 잠금으로 찾아 해제하고 `seat.released`를 발행한다 | [스케줄러 vs 만료 이벤트](./docs/trade-offs/2026-10-26-seat-ttl-scheduler-vs-keyspace-notification.md) |
| 실시간 반영 | 좌석 상태 변경을 커밋 이후 STOMP로 브로드캐스트하고, 입장 허용은 `entry.granted`를 릴레이해 알린다 | [브로드캐스트 방식](./docs/trade-offs/2026-10-28-websocket-broadcast-after-commit.md) |
| 실패 처리 | Consumer는 3회 재시도 후 DLT로 보낸다. 입장 요청 DLT는 1회 자동 재투입하고, 그래도 실패하면 사용자에게 다시 줄 서기를 안내한다 | [재시도/DLT](./docs/trade-offs/2026-10-28-consumer-retry-and-dlt.md), [DLT 재처리](./docs/trade-offs/2026-10-29-dlt-redrive-and-stats.md) |
| 통계 | 입장 허용/결제 완료/결제 실패/시간 초과를 Kafka로 구독해 Redis에 멱등 집계한다(Lua 스크립트) | [DLT 재처리와 통계](./docs/trade-offs/2026-10-29-dlt-redrive-and-stats.md) |
| 부하 제한 | 좌석 수는 서버가 정한 공연 템플릿(50~500석)에서만 나오고, 지난 회차는 24시간 뒤 자동 정리한다 | [공연 템플릿](./docs/trade-offs/2026-10-29-concert-template-runs.md), [회차 정리](./docs/trade-offs/2026-10-29-run-cleanup-scheduler.md) |

## 동작 흐름

```
공연 선택 ─ POST /concerts/{id}/runs ─▶ 새 회차(Event) + 좌석 생성
    │
입장 요청 ─ POST /events/{id}/entries ─▶ Redis 대기 줄(순번) ─▶ Kafka entry.requested
    │                                                              │
    │                                   Queue Worker (TPS 제한) ◀──┘  실패 시 재시도 → DLT → 재투입/최종 실패
    │                                         │
대기 화면 ◀─ 1초 폴링(순번/예상 시간) ─────────┤ 입장 토큰 발급(Redis) + entry.granted
          ◀─ WebSocket /topic/entries/{requestId} ─┘
    │
좌석 선택 ─ POST /seats/{id}/hold (X-Entry-Token) ─▶ Redis 락 → HELD ─▶ WebSocket /topic/events/{id}/seats
    │                                                   └ 5분 미결제 → 스케줄러 해제 → seat.released
결제 ─ POST /payments ─▶ Mock 게이트웨이 ─▶ CONFIRMED + seat.confirmed / AVAILABLE + seat.released
    │
예매 완료 ◀─ GET /events/{id}/stats (통계 Consumer 집계)
```

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

## 기술 스택

- **백엔드**: Java 17, Spring Boot 4.1, Spring Data JPA, Spring Kafka, Spring Data Redis, Spring WebSocket(STOMP)
- **인프라**: MySQL 8.4, Redis 7, Kafka 3.9(KRaft), Docker Compose
- **프론트엔드**: React 19, TypeScript, Vite, `@stomp/stompjs`
- **부하 테스트**: k6
- **테스트**: JUnit 5, Mockito, Testcontainers(Redis), EmbeddedKafka, H2

## 실행 방법

### 1. 인프라

```bash
docker compose up -d        # MySQL(root/ticketing) · Redis · Kafka
```

### 2. 백엔드

`backend/src/main/resources/application-local.properties.example`을 `application-local.properties`로 복사하고
DB 계정을 `root` / `ticketing`으로 바꾼다(이 파일은 git에 올라가지 않는다).

```bash
cd backend
./gradlew bootRun           # http://localhost:8080, local 프로필
```

8080 포트가 이미 쓰이고 있다면 `--args='--server.port=18080'`처럼 바꾼다. 기동 시 공연 10개가 자동 등록된다.

### 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173
```

백엔드 포트를 바꿨다면 `VITE_API_TARGET=http://localhost:18080 npm run dev`로 실행한다.

### 4. 가상 관객 (선택)

화면에 표시된 회차 번호로 k6 가상 관객을 같은 회차에 보낸다.

```bash
k6 run -e EVENT_ID=12 -e VUS=100 load-generator/queue.js
```

### 테스트

```bash
cd backend
./gradlew test              # Docker 필요 (Redis Testcontainers)
```

## 주요 API

모든 응답은 `{ "success": true, "data": ..., "error": null }` 형식이다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/v1/concerts` | 공연 목록 |
| POST | `/api/v1/concerts/{concertId}/runs` | 공연의 새 회차 생성 |
| POST | `/api/v1/events` | 이벤트 직접 생성 (k6용, 최대 500석) |
| GET | `/api/v1/events/{eventId}` | 회차 조회 |
| POST | `/api/v1/events/{eventId}/entries` | 입장 요청 (대기열, 202) |
| GET | `/api/v1/events/{eventId}/entries/{requestId}` | 입장 상태: 대기 순번/예상 시간, 토큰, 실패 |
| POST | `/api/v1/events/{eventId}/entries/direct` | 대기열 없이 즉시 입장 (부하 비교 기준선용) |
| GET | `/api/v1/events/{eventId}/seats` | 좌석 현황 |
| POST | `/api/v1/seats/{seatId}/hold` | 좌석 선점 (`X-Entry-Token` 헤더 필요) |
| POST | `/api/v1/seats/{seatId}/release` | 선점 해제 |
| POST | `/api/v1/payments` | Mock 결제 `{ "seatId": 1 }` |
| GET | `/api/v1/events/{eventId}/stats` | 회차 통계 |

WebSocket(STOMP) 엔드포인트는 `/ws`이고, `/topic/entries/{requestId}`(입장 허용), `/topic/events/{eventId}/seats`(좌석 상태)를 구독한다.

## Kafka 토픽

| 토픽 | 발행 | 구독 |
|---|---|---|
| `entry.requested` | 입장 요청 접수 | Queue Worker |
| `entry.requested.DLT` | 재시도 소진 | DLT 재처리 Consumer |
| `entry.granted` | 입장 허용 | WebSocket 릴레이, 통계 |
| `seat.confirmed` | 결제 완료 | 통계 |
| `seat.released` | 결제 실패 / 시간 초과 해제 | 통계 |

## 프로젝트 구조

```
backend/src/main/java/com/ticketing/api
├── domain
│   ├── concert   공연 템플릿, 회차 생성
│   ├── event     회차, 지난 회차 정리
│   ├── queue     입장 요청/대기 순번/입장 토큰, Queue Worker, DLT 재처리
│   ├── seat      좌석, Redis 락, TTL 해제, WebSocket 브로드캐스트
│   ├── payment   Mock 결제, seat.* 이벤트 발행
│   └── stats     통계 Consumer
└── global        Kafka/WebSocket 설정, 공통 응답, 예외 처리
frontend/src
├── pages         공연 목록, 대기, 좌석 선택, 결제, 예매 완료
└── shared        API 클라이언트, STOMP 훅, 공통 UI
load-generator    k6 시나리오 (direct.js / queue.js)
```

## 문서

- 설계: [`DESIGN.md`](./DESIGN.md)
- 도메인 정의: [`docs/domain`](./docs/domain)
- 기술 선택과 트레이드오프: [`docs/trade-offs`](./docs/trade-offs)
- 트러블슈팅: [`docs/troubleshooting`](./docs/troubleshooting)
- 작업 규칙: [`CLAUDE.md`](./CLAUDE.md)

## 범위 밖 / 남은 과제

- 배포 환경 구성 (DESIGN 9단계, 추후 결정)
- 사용자 인증 없음: 입장 토큰은 requestId를 아는 사람이 조회할 수 있다
- 대기열 우회용 `entries/direct`, STOMP 허용 Origin `*`는 로컬 시뮬레이션용이라 배포 시 막아야 한다
- 통계/릴레이 Consumer의 DLT는 보관만 하고 재처리하지 않는다
