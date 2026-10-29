# frontend

티케팅 시뮬레이터 화면 (Vite + React + TypeScript).
공연 목록 → 대기 → 좌석 선택 → 결제 → 예매 완료 순서로 진행하며, 대기 알림과 좌석 현황은 WebSocket(STOMP)으로 실시간 반영한다.

## 실행

```bash
npm install
npm run dev            # http://localhost:5173
```

개발 서버는 `/api`, `/ws` 요청을 백엔드로 프록시한다. 백엔드 주소 기본값은 `http://localhost:8080`이고,
다른 포트면 `VITE_API_TARGET=http://localhost:18080 npm run dev`처럼 바꾼다.

## 디자인

- 실제 예매 사이트 흐름을 따른다: 공연 목록(포스터 카드, 장르 탭) → 상세 창에서 예매하기 → 대기열 → 좌석 배치도 + 예매 정보 패널 → 결제(예매 정보, 결제 수단, 약관 동의) → 예매 완료(예매번호).
- 흰 바탕에 브랜드 레드 하나로 예매/결제 행동을 강조하고, 좌석 배치도만 색으로 상태(선택 가능/선택함/결제 진행 중/판매 완료)를 구분한다.
- 포스터 이미지는 없고 장르별 그라데이션과 공연명으로 그린다.
- 글꼴은 Pretendard(jsDelivr CDN)이고, 오프라인이면 시스템 글꼴로 대체된다.

## 구조

| 경로 | 역할 |
|---|---|
| `src/pages/*` | 단계별 화면 (concert-list, waiting-room, seat-selection, payment, result-dashboard) |
| `src/shared/api/client.ts` | 공통 응답 포맷을 푸는 fetch 래퍼와 API 타입 |
| `src/shared/websocket` | STOMP 구독 훅, 실시간 좌석 현황 훅 |
| `src/shared/ui` | 화면 간 공통 컴포넌트 |
