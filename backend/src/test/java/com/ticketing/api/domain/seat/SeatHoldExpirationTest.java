package com.ticketing.api.domain.seat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.domain.seat.dto.SeatResponse;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.lock.SeatLockManager;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.service.SeatService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * TTL이 지난 HELD 좌석이 AVAILABLE로 복구되고, 아직 유효한 선점은 유지되는지 검증하는 테스트.
 * 실제 Redis 컨테이너(Testcontainers)를 사용해서 SETNX의 원자성을 그대로 검증한다.
 */
@SpringBootTest
@Testcontainers
class SeatHoldExpirationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatLockManager seatLockManager;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EntryTokenManager entryTokenManager;

    private Long seatId;
    private String entryToken;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 남긴 좌석이 섞이지 않도록 매번 비운다
        seatRepository.deleteAll();
        eventRepository.deleteAll();
        Event event = eventRepository.save(Event.create("콘서트 TTL 만료 테스트", 1, LocalDateTime.now().plusDays(1)));
        entryToken = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();
        Seat seat = seatRepository.save(Seat.create(event, 1));
        seatId = seat.getId();
    }

    // 만료 기준 시각이 선점 시각 이후이면 HELD 좌석이 AVAILABLE로 복구되고 재선점이 가능해야 한다
    @Test
    @DisplayName("TTL이 지난 HELD 좌석은 AVAILABLE로 복구되어 다시 선점할 수 있다")
    void releaseExpiredSeats_expired_restoresAvailable() {
        // given
        seatService.holdSeat(seatId, entryToken);
        // 운영에서는 Redis TTL이 좌석 락과 토큰 선점 표시를 스스로 해제한다
        seatLockManager.forceUnlock(seatId);
        seatLockManager.clearHolder(entryToken);

        // when
        List<SeatResponse> released = seatService.releaseExpiredSeats(LocalDateTime.now().plusMinutes(1));

        // then
        assertThat(released).hasSize(1);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        seatService.holdSeat(seatId, entryToken);
    }

    // 선점 시각이 기준 시각 이후(아직 TTL 이내)인 좌석과 CONFIRMED 좌석은 건드리지 않아야 한다
    @Test
    @DisplayName("TTL 이내의 HELD 좌석은 복구되지 않는다")
    void releaseExpiredSeats_notExpired_keepsHeld() {
        // given
        seatService.holdSeat(seatId, entryToken);

        // when
        List<SeatResponse> released = seatService.releaseExpiredSeats(LocalDateTime.now().minusMinutes(1));

        // then
        assertThat(released).isEmpty();
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
