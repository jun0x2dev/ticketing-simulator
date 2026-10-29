package com.ticketing.api.domain.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.api.domain.payment.dto.SeatConfirmedEvent;
import com.ticketing.api.domain.payment.dto.SeatReleasedEvent;
import com.ticketing.api.domain.stats.dto.EventStatsResponse;
import com.ticketing.api.domain.stats.service.EventStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 실제 Redis에서 통계가 항목별로 집계되고, 같은 메시지가 다시 와도 한 번만 세는지(Lua 스크립트) 검증한다.
 */
@SpringBootTest
@Testcontainers
class EventStatsIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private EventStatsService statsService;

    // 같은 결제 완료 메시지가 두 번 와도 1건으로 세고, 해제 사유별로 따로 집계되어야 한다
    @Test
    @DisplayName("중복 메시지는 한 번만 세고 항목별로 집계한다")
    void record_countsOnceAndByField() {
        // given
        SeatConfirmedEvent confirmed = SeatConfirmedEvent.of(201L, 1L, 1001L);
        SeatReleasedEvent ttlExpired = SeatReleasedEvent.ttlExpired(201L, 2L);

        // when
        statsService.recordConfirmed(confirmed);
        statsService.recordConfirmed(confirmed);
        statsService.recordConfirmed(SeatConfirmedEvent.of(201L, 3L, 1002L));
        statsService.recordReleased(ttlExpired);
        statsService.recordReleased(ttlExpired);
        statsService.recordReleased(SeatReleasedEvent.paymentFailed(201L, 4L, 1003L));

        // then
        EventStatsResponse stats = statsService.getStats(201L);
        assertThat(stats.confirmed()).isEqualTo(2);
        assertThat(stats.ttlExpired()).isEqualTo(1);
        assertThat(stats.paymentFailed()).isEqualTo(1);
        assertThat(stats.granted()).isZero();
    }

    // 다른 회차의 통계와 섞이지 않아야 한다
    @Test
    @DisplayName("회차별 통계는 서로 섞이지 않는다")
    void stats_areSeparatedByEvent() {
        // when
        statsService.recordConfirmed(SeatConfirmedEvent.of(202L, 1L, 2001L));

        // then
        assertThat(statsService.getStats(202L).confirmed()).isEqualTo(1);
        assertThat(statsService.getStats(203L).confirmed()).isZero();
    }
}
