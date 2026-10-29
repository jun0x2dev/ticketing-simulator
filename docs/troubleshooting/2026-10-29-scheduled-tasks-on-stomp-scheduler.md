# @Scheduled 작업이 WebSocket(STOMP) 스케줄러 스레드에서 실행됨

## 증상

- outbox 발행기를 붙이고 실서버 SQL 로그를 보니, 발행 쿼리(`... for update of oe1_0 skip locked`)가 `MessageBroker-2` 스레드에서 실행되고 있었다. TTL 복구 쿼리도 같은 스레드 풀에서 돌았다.
- `spring.task.scheduling.pool.size=4`를 설정했는데도 `scheduling-*` 스레드가 하나도 생기지 않았다.

## 원인

- `@EnableWebSocketMessageBroker`는 STOMP 하트비트용 `TaskScheduler` 빈(`messageBrokerTaskScheduler`)을 등록한다.
- Spring Boot의 `@Scheduled`용 스케줄러 자동 설정은 `TaskScheduler` 빈이 하나라도 있으면 만들어지지 않는다(`@ConditionalOnMissingBean`). 그래서 `spring.task.scheduling.*` 설정도 적용되지 않았다.
- `@Scheduled` 처리기는 `taskScheduler`라는 이름의 빈이 없고 `TaskScheduler` 빈이 하나뿐이면 그것을 쓴다. 결과적으로 TTL 복구, 회차 정리, outbox 발행이 STOMP 하트비트 스레드를 나눠 썼다.
- outbox 발행은 브로커 응답을 최대 수 초 기다리므로, 브로커 장애 시 WebSocket 하트비트와 서로 밀어낼 수 있다.

## 해결

- `global/config/SchedulingConfig`에 `taskScheduler`라는 이름의 `ThreadPoolTaskScheduler` 빈을 두었다. Spring Boot의 `ThreadPoolTaskSchedulerBuilder`로 만들어서 `spring.task.scheduling.pool.size`(4)와 스레드 이름(`scheduling-`) 설정이 그대로 적용된다.
- `@Scheduled`는 이름이 `taskScheduler`인 빈을 우선해서 쓰고, STOMP는 자기 스케줄러를 계속 쓴다. 실서버에서 발행/TTL 복구 쿼리가 `scheduling-1`~`scheduling-4` 스레드에서 실행되는 것을 로그로 확인했다.
