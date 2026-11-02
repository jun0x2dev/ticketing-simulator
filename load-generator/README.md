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
k6 run -e VUS=1000 -e SEAT_COUNT=500 load-generator/direct.js
k6 run -e VUS=1000 -e SEAT_COUNT=500 load-generator/queue.js
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

화면에서 예매 중인 회차에 가상 관객을 보내려면 화면에 표시된 회차 번호를 `EVENT_ID`로 넘긴다.

```bash
k6 run -e EVENT_ID=12 -e VUS=100 load-generator/queue.js
```

좌석 수(`SEAT_COUNT`)는 서버 상한이 500석이다.

서버는 오픈 시각 전 입장을 거절하므로, 새 이벤트는 3초 뒤 열리게 만들고 setup이 오픈 1초 뒤까지 기다린 다음 가상 관객을 한꺼번에 보낸다. 오픈 시각은 UTC(`Z`)로 보내므로 k6 컨테이너와 서버의 시간대가 달라도 된다.

선점 경쟁에서 지는 409는 정상 결과로 보고 `http_req_failed`에서 제외한다.
Queue Worker 처리량은 앱 설정 `queue.worker.tps`(기본 100)로 조절한다.
