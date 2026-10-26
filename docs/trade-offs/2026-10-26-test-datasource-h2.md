# 테스트 환경 DB를 H2로 분리

## 선택지

- 로컬 MySQL을 테스트에도 그대로 사용
- Testcontainers로 실제 MySQL 컨테이너를 띄워 테스트
- H2 인메모리 DB를 테스트 전용으로 사용

## 선택

`src/test/resources/application.properties`에 H2(`jdbc:h2:mem:...;MODE=MySQL`)를 설정해서
`ApplicationContext` 로딩 테스트(`TicketingApiApplicationTests`)가 외부 인프라 없이도 동작하게 함.

## 이유

- 로컬 MySQL을 그대로 쓰면 MySQL이 꺼져 있을 때 전체 테스트가 실패해서, CI 환경이나 다른 개발자 환경에서
  재현성이 떨어짐.
- Testcontainers가 더 정확한 통합 테스트지만, 지금 단계는 "컨텍스트가 뜨는지" 확인하는 수준이라
  과한 선택. 실제 Repository 슬라이스 테스트를 작성하는 시점에 Testcontainers 도입을 재검토한다.

## 추가 반영 (2026-10-26)

- 운영 코드(`EventController`)에 있던 Kafka 수동 테스트용 엔드포인트가 보안 리뷰에서 "인증 없는 디버그
  엔드포인트 노출"로 지적됐다. 엔드포인트를 제거하고, 그 역할을 `EntryRequestKafkaIntegrationTest`
  (`@EmbeddedKafka` 기반)로 대체했다. 이로써 CLAUDE.md 3번 섹션의 "Kafka Consumer/Producer는
  EmbeddedKafka 또는 Testcontainers로 통합 테스트" 규칙도 충족한다.
- 직렬화 과정에서 `java.time.Instant` 처리를 위해 `jackson-datatype-jsr310` 의존성이 빠져있던 걸
  발견해서 추가했다 (관련 내용은 `docs/troubleshooting/2026-10-26-kafka-instant-serialization.md` 참고).
