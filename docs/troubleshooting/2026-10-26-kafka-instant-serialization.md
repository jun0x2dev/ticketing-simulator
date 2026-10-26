# Kafka 메시지 발행 시 Instant 직렬화 실패

## 증상

`EntryRequestKafkaIntegrationTest` 실행 시 `EntryRequestProducer.send()` 호출에서 아래 예외 발생:

```
org.apache.kafka.common.errors.SerializationException: Can't serialize data [...] for topic [entry.requested]
Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Java 8 date/time type
`java.time.Instant` not supported by default: add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310"
to enable handling (...)
```

## 원인

`EntryRequestedEvent`의 `requestedAt` 필드가 `java.time.Instant`인데, Kafka `JsonSerializer`가 내부적으로
쓰는 Jackson `ObjectMapper`에 JSR-310(Java 8 날짜/시간) 모듈이 등록되어 있지 않았다.
`build.gradle`에 `jackson-datatype-jsr310` 의존성이 명시적으로 선언되어 있지 않았던 것이 원인.

## 해결

`build.gradle`의 `implementation`에 `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`을 추가.
Spring Kafka의 `JsonSerializer`는 클래스패스에서 Jackson 모듈을 자동 탐지(`ObjectMapper.findModules()`)
하므로, 의존성만 추가하면 별도 설정 없이 `Instant` 직렬화/역직렬화가 정상 동작한다.

## 참고

- 관련 커밋: `fix/remove-debug-endpoint-add-comments` 브랜치
- 관련 테스트: `EntryRequestKafkaIntegrationTest`
