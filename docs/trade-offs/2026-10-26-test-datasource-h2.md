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

## 보류된 것

- CLAUDE.md 3번 섹션은 Kafka Consumer/Producer를 EmbeddedKafka 또는 Testcontainers로 통합 테스트하라고
  명시하는데, 이번 작업에서는 아직 추가하지 않았다. `EntryRequestConsumer`가 현재 로그만 남기는
  placeholder라 통합 테스트의 가치가 낮아 보류했고, 실제 대기열 처리 로직(토큰 발급 등)이 들어가는 다음
  단계에서 EmbeddedKafka 기반 통합 테스트를 추가할 예정.
