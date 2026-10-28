// 대기열 적용 전/후 시나리오가 공유하는 설정과 헬퍼.
import http from 'k6/http';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const SEAT_COUNT = Number(__ENV.SEAT_COUNT || 1000);
export const VUS = Number(__ENV.VUS || 1000);

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 선점 경쟁(409)은 정상 결과이므로 http_req_failed에 포함하지 않는다.
export const HOLD_EXPECTED = http.expectedStatuses(200, 409);

/** 이벤트를 만들고 좌석 ID 목록을 반환한다. 모든 VU가 같은 이벤트를 두고 경쟁한다. */
export function setupEvent() {
  // 서버와 시간대가 달라도 @Future를 만족하도록 넉넉하게 이틀 뒤로 잡는다.
  const openAt = new Date(Date.now() + 48 * 3600 * 1000).toISOString().slice(0, 19);
  const created = http.post(
    `${BASE_URL}/api/v1/events`,
    JSON.stringify({ name: `부하 테스트 ${Date.now()}`, seatCount: SEAT_COUNT, openAt }),
    { headers: JSON_HEADERS },
  );
  const eventId = created.json('data.id');
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
