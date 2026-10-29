# 역직렬화할 수 없는 Kafka 메시지 하나가 파티션 처리를 영구히 멈춤

## 증상

- `entry.requested`에 JSON이 아닌 메시지(`not-json`) 하나를 넣고, 바로 뒤에 정상 입장 요청을 넣었다. 30초 동안 `RecordDeserializationException`이 약 66만 번 반복됐고, 뒤의 정상 요청은 끝내 처리되지 않았다(임베디드 Kafka로 재현).
- 실서비스라면 그 파티션에 들어온 모든 입장 요청이 멈추고, 로그와 CPU가 폭주한다. 통계 Consumer(`seat.*`, `entry.granted`)도 같은 구조였다.

## 원인

- Consumer의 값 역직렬화기가 `JsonDeserializer`를 그대로 썼다. 역직렬화는 `KafkaConsumer.poll()` 안에서 일어나므로, 실패하면 레코드가 리스너나 에러 핸들러(재시도/DLT)에 전달되기 전에 예외가 난다.
- 오프셋이 넘어가지 않으니 다음 poll에서 같은 메시지를 다시 읽고 다시 실패한다. 재시도 3회 후 DLT로 보내는 `DefaultErrorHandler`가 개입할 기회가 없었다.

## 해결

- 값 역직렬화기를 `ErrorHandlingDeserializer`로 감싸고, 실제 변환은 `JsonDeserializer`에 맡겼다(`spring.deserializer.value.delegate.class`). 역직렬화 실패는 예외 대신 헤더로 표시되어 에러 핸들러로 넘어간다. `DeserializationException`은 재시도 대상이 아니라 바로 DLT로 간다.
- 역직렬화 실패 레코드는 원본 바이트로 DLT에 보내진다. JSON 직렬화기로 보내면 base64 문자열로 바뀌므로, `DeadLetterPublishingRecoverer`에 `byte[]` 전용 템플릿(`ByteArraySerializer`)을 따로 줘서 원본 그대로 보관한다.
- DLT를 소비하다 실패한 메시지(예: DLT에 들어간 깨진 메시지)를 다시 `*.DLT.DLT`로 보내지 않도록, DLT 토픽이면 목적지를 null로 돌려 error 로그만 남기고 건너뛴다. 입장 요청 DLT Consumer는 값이 없는 메시지도 건너뛴다.
- 직렬화 설정이 git에 올라가지 않는 프로필 파일(`application-local.properties`)에만 있어서, 공통 `application.properties`로 옮겼다. 로컬 프로필에는 접속 정보만 남긴다.
- `EntryRequestPoisonMessageIntegrationTest`로 회귀를 막는다(깨진 메시지가 DLT에 원본 그대로 쌓이고, 뒤의 정상 메시지가 처리되며, DLT Consumer도 멈추지 않음). 실서버 Kafka에 `kafka-console-producer`로 깨진 메시지를 넣어도 같은 결과였고, 역직렬화 예외 반복은 0건이었다.
