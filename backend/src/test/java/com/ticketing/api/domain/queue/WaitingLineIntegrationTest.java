package com.ticketing.api.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.api.domain.queue.waiting.WaitingLineManager;
import com.ticketing.api.domain.queue.waiting.WaitingPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 Redis로 대기 순번이 접수 순서대로 매겨지고, 앞사람이 입장하면 순번이 당겨지는지 검증한다.
 * 테스트마다 다른 eventId를 써서 서로의 대기 줄이 섞이지 않게 한다.
 */
@SpringBootTest
@Testcontainers
class WaitingLineIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WaitingLineManager waitingLineManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 접수 순서대로 1, 2, 3번이 되고, 뒤 인원은 줄 길이 기준으로 계산되어야 한다
    @Test
    @DisplayName("입장 요청은 접수 순서대로 대기 순번을 받는다")
    void join_assignsPositionsInOrder() {
        // given
        waitingLineManager.join(101L, "a");
        waitingLineManager.join(101L, "b");
        waitingLineManager.join(101L, "c");

        // when & then
        assertThat(waitingLineManager.findPosition(101L, "a")).contains(new WaitingPosition(1, 2));
        assertThat(waitingLineManager.findPosition(101L, "b")).contains(new WaitingPosition(2, 1));
        assertThat(waitingLineManager.findPosition(101L, "c")).contains(new WaitingPosition(3, 0));
    }

    // 앞사람이 입장해 줄에서 빠지면 뒷사람의 순번이 하나씩 당겨져야 한다
    @Test
    @DisplayName("앞사람이 줄에서 빠지면 뒷사람의 순번이 당겨진다")
    void leave_movesBackPeopleForward() {
        // given
        waitingLineManager.join(102L, "a");
        waitingLineManager.join(102L, "b");

        // when
        waitingLineManager.leave(102L, "a");

        // then
        assertThat(waitingLineManager.findPosition(102L, "a")).isEmpty();
        assertThat(waitingLineManager.findPosition(102L, "b")).contains(new WaitingPosition(1, 0));
    }

    // 다른 이벤트의 대기 줄은 서로 영향을 주지 않아야 한다
    @Test
    @DisplayName("이벤트마다 대기 줄이 따로 관리된다")
    void lines_areSeparatedByEvent() {
        // given
        waitingLineManager.join(103L, "x");
        waitingLineManager.join(104L, "y");

        // when & then
        assertThat(waitingLineManager.findPosition(103L, "x")).contains(new WaitingPosition(1, 0));
        assertThat(waitingLineManager.findPosition(104L, "y")).contains(new WaitingPosition(1, 0));
        assertThat(waitingLineManager.findPosition(103L, "y")).isEmpty();
    }

    // 처리되지 못한 요청이 영구히 남지 않도록 대기 줄 키에 만료 시간이 있어야 한다
    @Test
    @DisplayName("대기 줄에는 만료 시간이 설정된다")
    void join_setsTtl() {
        // when
        waitingLineManager.join(105L, "a");

        // then
        assertThat(redisTemplate.getExpire("queue:line:105")).isPositive();
        assertThat(redisTemplate.getExpire("queue:seq:105")).isPositive();
    }
}
