# queue

## 정의

대규모 입장 요청을 Kafka로 완충하는 대기열 도메인. entity/repository 없이 producer/consumer와
이벤트 DTO로만 구성된다.

## 주요 상태/흐름

- 가상 유저(혹은 테스트 호출)가 입장을 요청하면 `EntryRequestProducer`가 `entry.requested` 토픽에 발행한다.
- `EntryRequestConsumer`는 현재 **수신 로그만 남기는 placeholder** 상태다.
  - 실제 대기열 처리(TPS 제한 소비, 입장 허용 토큰 발급, WebSocket으로 순번 브로드캐스트)는
    simulation/seat 도메인 구현과 함께 추가될 예정.
- 파티션 키는 `eventId` — 같은 이벤트(공연)의 요청은 순서가 보장된다.

## 관련 이벤트 (Kafka 토픽)

| 토픽 | Producer | Consumer | 설명 |
|---|---|---|---|
| `entry.requested` | queue.producer (EntryRequestProducer) | queue.consumer (EntryRequestConsumer) | 입장 요청 적재. 현재 consumer는 로그만 남김 |
| `entry.granted` | (미구현) | (미구현) | 입장 허용 알림 — 토픽만 선언됨 |
| `seat.confirmed` | payment.producer (SeatEventProducer) | (미구현) | 결제 완료 좌석 |
| `seat.released` | payment.producer (SeatEventProducer) | (미구현) | 결제 실패 좌석. TTL 만료 해제 시 발행은 미구현 |

토픽 정의는 `global.config.KafkaTopicConfig`에서 관리한다.
