# Kafka 메시지 직렬화 방식 선택

## 선택지

- String 직렬화 후 수동으로 Jackson ObjectMapper를 사용해 JSON 변환
- Spring Kafka의 `JsonSerializer`/`JsonDeserializer` 사용

## 선택

`JsonSerializer`/`JsonDeserializer`를 `KafkaTemplate`/`@KafkaListener`에 그대로 적용.

## 이유

- 메시지 바디를 `EntryRequestedEvent` 같은 record DTO로 그대로 주고받을 수 있어서 Producer/Consumer
  양쪽에서 수동 파싱 코드가 없어진다.
- Consumer 쪽은 `spring.kafka.consumer.properties.spring.json.trusted.packages`로 역직렬화 허용 패키지를
  `com.ticketing.api.domain.queue.dto`로 제한해서, 임의 클래스 역직렬화로 인한 보안 문제를 피했다.
- 트레이드오프: 메시지에 타입 정보(`__TypeId__` 헤더)가 포함되어 메시지 크기가 약간 커지고, Consumer가
  Producer와 동일한 DTO 클래스 경로를 알아야 한다(서비스가 분리되면 별도 공유 모듈이나 스키마 레지스트리가
  필요해질 수 있음 — 지금은 모놀리식이라 문제 없음).
