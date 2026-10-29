# 앱 기동 직후 공연 목록이 비어 있음

## 증상

실제 서버로 확인할 때, 앱이 요청을 받기 시작하자마자 호출한 `GET /api/v1/concerts`가 빈 목록(`[]`)을 반환했다. 몇 초 뒤 다시 호출하면 10개가 정상으로 나왔다.

## 원인

기본 공연 시드를 `ApplicationRunner`로 넣고 있었다. Spring Boot는 컨텍스트 refresh 마지막에 내장 웹 서버를 먼저 시작하고, `ApplicationRunner`는 그 이후에 실행한다. 그 사이에 들어온 요청은 시드 전의 빈 테이블을 조회한다.

## 해결

`ConcertCatalogInitializer`를 `SmartInitializingSingleton`으로 바꿨다. `afterSingletonsInstantiated()`는 모든 싱글톤(JPA, 트랜잭션 프록시 포함)이 준비된 뒤, 웹 서버가 시작되기 전에 호출되므로 서버가 요청을 받는 시점에는 시드가 끝나 있다.
