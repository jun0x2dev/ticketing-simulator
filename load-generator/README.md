# load-generator

k6로 "오픈 직후 N명이 동시에 입장 → 좌석 선점" 상황을 재현하고, 대기열 적용 전/후를 비교한다.

| 스크립트 | 시나리오 |
|---|---|
| `direct.js` | 대기열 적용 전. `POST /entries/direct`로 토큰을 즉시 받고 전원이 동시에 선점 시도 |
| `queue.js` | 대기열 적용 후. `POST /entries`(202) → 큐 워커가 TPS 제한으로 허용 → 폴링 후 선점 시도 |

## 실행

```bash
docker compose up -d                  # MySQL / Redis / Kafka
# 앱 실행 후 (Windows PowerShell 예시는 아래 환경변수 참고)
k6 run -e VUS=1000 -e SEAT_COUNT=1000 load-generator/direct.js
k6 run -e VUS=1000 -e SEAT_COUNT=1000 load-generator/queue.js
```

k6가 없으면 `docker run --rm -i --network host -v "$PWD/load-generator:/s" grafana/k6 run -e BASE_URL=... /s/direct.js`.

README의 결과는 아래 조건으로 측정했다 (앱 실행 시 추가한 설정).

```properties
server.port=18080
server.tomcat.threads.max=20
spring.datasource.hikari.maximum-pool-size=5
queue.worker.tps=20
```

```bash
docker run --rm -i --cpus 1 -v "$PWD/load-generator:/s" grafana/k6 run \
  -e BASE_URL=http://host.docker.internal:18080 -e VUS=300 -e SEAT_COUNT=100 /s/direct.js
```

선점 경쟁에서 지는 409는 정상 결과로 보고 `http_req_failed`에서 제외한다.
Queue Worker 처리량은 앱 설정 `queue.worker.tps`(기본 100)로 조절한다.
