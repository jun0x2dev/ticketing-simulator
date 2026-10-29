// 대기열 적용 후: 입장 요청을 접수(202)하고, 큐 워커가 허용할 때까지 폴링한 뒤 좌석 선점을 시도한다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, VUS, setupEvent, holdRandomSeat } from './scenario.js';

const waitTime = new Trend('queue_wait_ms', true);

export const options = {
  // 요청 종류별(entry/hold) 지표를 요약에 따로 출력하기 위한 빈 threshold.
  thresholds: {
    'http_req_duration{name:entry}': [],
    'http_req_duration{name:hold}': [],
    'http_req_duration{name:poll}': [],
    queue_wait_ms: [],
  },
  scenarios: {
    burst: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '10m' },
  },
};

export function setup() {
  return setupEvent();
}

export default function (data) {
  const accepted = http.post(`${BASE_URL}/api/v1/events/${data.eventId}/entries`, null, {
    tags: { name: 'entry' },
  });
  if (!check(accepted, { 'entry accepted': (r) => r.status === 202 })) return;

  const requestId = accepted.json('data.requestId');
  const start = Date.now();
  while (Date.now() - start < 9 * 60 * 1000) {
    const status = http.get(`${BASE_URL}/api/v1/events/${data.eventId}/entries/${requestId}`, { tags: { name: 'poll' } });
    if (status.json('data.status') === 'GRANTED') {
      waitTime.add(Date.now() - start);
      holdRandomSeat(data, status.json('data.token'));
      return;
    }
    sleep(1);
  }
}
