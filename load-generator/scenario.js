// 대기열 적용 전/후 시나리오가 공유하는 설정과 헬퍼.
import http from 'k6/http';
import { sleep } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const SEAT_COUNT = Number(__ENV.SEAT_COUNT || 300);
// 지정하면 새 이벤트를 만들지 않고 이미 진행 중인 회차(화면에서 예매 중인 회차)에 가상 관객을 보낸다.
const EVENT_ID = __ENV.EVENT_ID;
export const VUS = Number(__ENV.VUS || 1000);

// 새 이벤트를 만들 때 오픈까지 남겨 두는 시간. 생성 요청이 서버에 닿기 전에 오픈 시각이 지나 @Future 검증에 걸리지 않을 만큼 둔다.
const OPEN_DELAY_MS = 3000;

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 선점 경쟁(409)은 정상 결과이므로 http_req_failed에 포함하지 않는다.
export const HOLD_EXPECTED = http.expectedStatuses(200, 409);

/** 이벤트를 만들고(또는 EVENT_ID 회차를 쓰고) 좌석 ID 목록을 반환한다. 모든 VU가 같은 이벤트를 두고 경쟁한다. */
export function setupEvent() {
  if (EVENT_ID) {
    const seats = http.get(`${BASE_URL}/api/v1/events/${EVENT_ID}/seats`).json('data');
    return { eventId: Number(EVENT_ID), seatIds: seats.map((s) => s.id) };
  }
  // 서버는 오픈 시각 전 입장을 거절하므로, 곧 열리는 회차를 만들고 열릴 때까지 기다린 뒤 전원이 동시에 몰린다.
  // UTC(Z)로 보내므로 k6와 서버의 시간대가 달라도 같은 순간을 가리킨다.
  const openAtMs = Date.now() + OPEN_DELAY_MS;
  const created = http.post(
    `${BASE_URL}/api/v1/events`,
    JSON.stringify({ name: `부하 테스트 ${Date.now()}`, seatCount: SEAT_COUNT, openAt: new Date(openAtMs).toISOString() }),
    { headers: JSON_HEADERS },
  );
  if (created.status !== 201) {
    throw new Error(`이벤트 생성 실패: ${created.status} ${created.body}`);
  }
  const eventId = created.json('data.id');
  // k6와 서버 시계가 조금 어긋나도 오픈 전에 요청하지 않도록 1초 더 기다린다.
  sleep(Math.max(0, openAtMs - Date.now()) / 1000 + 1);
  const seats = http.get(`${BASE_URL}/api/v1/events/${eventId}/seats`).json('data');
  return { eventId, seatIds: seats.map((s) => s.id) };
}

/** 입장 토큰으로 무작위 좌석 선점을 시도한다. */
export function holdRandomSeat(data, token) {
  const seatId = data.seatIds[Math.floor(Math.random() * data.seatIds.length)];
  return http.post(`${BASE_URL}/api/v1/seats/${seatId}/hold`, null, {
    headers: { 'X-Entry-Token': token },
    responseCallback: HOLD_EXPECTED,
    tags: { name: 'hold' },
  });
}
