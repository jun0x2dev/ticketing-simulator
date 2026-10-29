# frontend

티케팅 시뮬레이터 화면 (Vite + React + TypeScript).
설정 → 대기 → 좌석 선택 → 결제 → 결과 순서로 진행하며, 대기 알림과 좌석 현황은 WebSocket(STOMP)으로 실시간 반영한다.

## 실행

```bash
npm install
npm run dev            # http://localhost:5173
```

개발 서버는 `/api`, `/ws` 요청을 백엔드로 프록시한다. 백엔드 주소 기본값은 `http://localhost:8080`이고,
다른 포트면 `VITE_API_TARGET=http://localhost:18080 npm run dev`처럼 바꾼다.

## 디자인

- 공연장 객석을 모티프로 한다. 선택 가능 좌석은 벨벳색, 결제 대기는 무대 조명 앰버, 판매 완료는 회색이다.
- 결제·결과 화면은 절취선이 있는 종이 티켓 모양이고, 결제가 거절되면 "결제 거절" 도장이 찍힌다.
- 글꼴은 Google Fonts의 Black Han Sans(제목, 숫자)와 IBM Plex Sans KR(본문)을 쓴다. 오프라인이면 시스템 글꼴로 대체된다.

## 구조

| 경로 | 역할 |
|---|---|
| `src/pages/*` | 단계별 화면 (simulation-setup, waiting-room, seat-selection, payment, result-dashboard) |
| `src/shared/api/client.ts` | 공통 응답 포맷을 푸는 fetch 래퍼와 API 타입 |
| `src/shared/websocket` | STOMP 구독 훅, 실시간 좌석 현황 훅 |
| `src/shared/ui` | 화면 간 공통 컴포넌트 |
