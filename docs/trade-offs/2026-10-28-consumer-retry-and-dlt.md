# Consumer 재시도 및 DLT 전략

## 선택지

- 에러 핸들러 기본값 사용(Spring Kafka 기본 재시도 후 로그만 남기고 스킵)
- `DefaultErrorHandler` + 고정 간격 재시도 + `DeadLetterPublishingRecoverer`
- 재시도 토픽 체인(`@RetryableTopic`)

## 선택

`DefaultErrorHandler`에 `FixedBackOff(1000ms, 3회)`를 두고, 소진되면 `{원본 토픽}.DLT`로 보낸다. DLT 토픽명은 `TopicPartition` resolver로 직접 지정한다.

## 이유

- 프로젝트 규칙(무한 재시도 금지, 최대 횟수 후 DLQ)을 가장 적은 설정으로 만족한다. 같은 파티션 번호로 보내 키 단위 순서 정보를 유지한다.
- 현재 Spring Kafka 버전의 DLT 기본 접미사가 `.DLT`가 아니라 `-dlt`여서, 기본값을 쓰면 `KafkaTopicConfig`에 선언한 `entry.requested.DLT`와 어긋났다(테스트에서 발견). 기본값에 기대지 않고 이름을 명시한다.
- 감수한 점: 재시도 동안 같은 파티션 처리가 막힌다(블로킹 재시도). 재시도 토픽 체인은 이를 피하지만 토픽/설정이 늘어 MVP에는 과하다. DLT를 소비·재처리하는 로직은 아직 없다.
