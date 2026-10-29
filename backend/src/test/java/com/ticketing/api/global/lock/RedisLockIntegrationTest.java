package com.ticketing.api.global.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
 * 실제 Redis로 RedisLock의 획득/해제 규칙(내 락만 해제, TTL 설정)을 검증한다.
 */
@SpringBootTest
@Testcontainers
class RedisLockIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisLock redisLock;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 이미 잡힌 락은 다시 잡을 수 없고, 잡은 락에는 TTL이 있어야 한다
    @Test
    @DisplayName("이미 잡힌 락은 획득할 수 없고 잡은 락에는 TTL이 설정된다")
    void tryLock_secondAttemptFails_andHasTtl() {
        // when
        String first = redisLock.tryLock("lock:test:ttl", Duration.ofSeconds(30));
        String second = redisLock.tryLock("lock:test:ttl", Duration.ofSeconds(30));

        // then
        assertThat(first).isNotNull();
        assertThat(second).isNull();
        assertThat(redisTemplate.getExpire("lock:test:ttl")).isPositive();
    }

    // TTL 만료 뒤 다른 요청이 새로 잡은 락을, 예전 토큰으로는 지울 수 없어야 한다
    @Test
    @DisplayName("다른 토큰으로는 락을 해제할 수 없고, 획득한 토큰으로만 해제된다")
    void unlock_onlyOwnerCanRelease() {
        // given
        String token = redisLock.tryLock("lock:test:owner", Duration.ofSeconds(30));

        // when & then
        assertThat(redisLock.unlock("lock:test:owner", "stale-token")).isFalse();
        assertThat(redisTemplate.hasKey("lock:test:owner")).isTrue();
        assertThat(redisLock.unlock("lock:test:owner", token)).isTrue();
        assertThat(redisTemplate.hasKey("lock:test:owner")).isFalse();
    }
}
