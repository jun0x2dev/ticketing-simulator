// 대기열 적용 전: 모든 가상 유저가 입장 토큰을 즉시 받고 동시에 좌석 선점을 시도한다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, VUS, setupEvent, holdRandomSeat } from './scenario.js';

export const options = {
  // 요청 종류별(entry/hold) 지표를 요약에 따로 출력하기 위한 빈 threshold.
  thresholds: {
    'http_req_duration{name:entry}': [],
    'http_req_duration{name:hold}': [],
  },
  scenarios: {
    burst: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '3m' },
  },
};

export function setup() {
  return setupEvent();
}

export default function (data) {
  const entry = http.post(`${BASE_URL}/api/v1/events/${data.eventId}/entries/direct`, null, {
    tags: { name: 'entry' },
  });
  if (!check(entry, { 'entry granted': (r) => r.status === 200 })) return;
  holdRandomSeat(data, entry.json('data.token'));
}
