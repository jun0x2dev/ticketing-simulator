package com.ticketing.api.domain.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * 실제 Redis로 입장 토큰의 발급 멱등성/TTL과, 좌석 선점 시 토큰 검증(없음/타 이벤트 토큰)을 검증한다.
 */
@SpringBootTest
@Testcontainers
class EntryTokenIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private EntryTokenManager entryTokenManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EventRepository eventRepository;

    private Event event;
    private Long seatId;

    @BeforeEach
    void setUp() {
        // 다른 테스트가 남긴 데이터가 섞이지 않도록 매번 비운다
        seatRepository.deleteAll();
        eventRepository.deleteAll();
        event = eventRepository.save(Event.create("콘서트 토큰 테스트", 1, LocalDateTime.now().plusDays(1)));
        seatId = seatRepository.save(Seat.create(event, 1)).getId();
    }

    // 같은 requestId로 두 번 발급하면 두 번째는 발급되지 않고, 첫 토큰이 그대로 조회되어야 한다
    @Test
    @DisplayName("같은 requestId로는 토큰이 한 번만 발급된다")
    void issue_sameRequestId_issuedOnlyOnce() {
        // given
        String requestId = UUID.randomUUID().toString();

        // when
        String first = entryTokenManager.issue(requestId, event.getId()).orElseThrow();
        boolean secondIssued = entryTokenManager.issue(requestId, event.getId()).isPresent();

        // then
        assertThat(secondIssued).isFalse();
        assertThat(entryTokenManager.findToken(requestId)).contains(first);
        assertThat(entryTokenManager.findEventId(first)).contains(event.getId());
    }

    // 발급된 토큰 키에는 TTL이 걸려 있어야 영구히 남지 않는다
    @Test
    @DisplayName("발급된 토큰에는 만료 시간이 설정된다")
    void issue_setsTtl() {
        // when
        String token = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();

        // then
        Long ttl = redisTemplate.getExpire("entry:token:" + token);
        assertThat(ttl).isPositive();
    }

    // 유효한 토큰으로는 좌석을 선점할 수 있어야 한다
    @Test
    @DisplayName("유효한 입장 토큰으로 좌석을 선점할 수 있다")
    void holdSeat_withValidToken_succeeds() {
        // given
        String token = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();

        // when & then
        assertThat(seatService.holdSeat(seatId, token).id()).isEqualTo(seatId);
    }

    // 토큰이 없거나 존재하지 않는 값이면 락을 잡기 전에 거절되어야 한다
    @Test
    @DisplayName("토큰이 없거나 유효하지 않으면 좌석을 선점할 수 없다")
    void holdSeat_withoutOrInvalidToken_throws() {
        // when & then
        assertThatThrownBy(() -> seatService.holdSeat(seatId, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTRY_TOKEN_INVALID);
        assertThatThrownBy(() -> seatService.holdSeat(seatId, "unknown-token"))
                .isInstanceOf(CustomException.class);
    }

    // 다른 이벤트용 토큰으로는 이 이벤트의 좌석을 잡을 수 없고, 실패 후에도 좌석 락이 남지 않아야 한다
    @Test
    @DisplayName("다른 이벤트의 입장 토큰으로는 좌석을 선점할 수 없다")
    void holdSeat_withOtherEventToken_throwsAndReleasesLock() {
        // given
        Event other = eventRepository.save(Event.create("다른 콘서트", 1, LocalDateTime.now().plusDays(1)));
        String otherToken = entryTokenManager.issue(UUID.randomUUID().toString(), other.getId()).orElseThrow();
        String ownToken = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();

        // when & then
        assertThatThrownBy(() -> seatService.holdSeat(seatId, otherToken))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTRY_TOKEN_INVALID);
        assertThat(seatService.holdSeat(seatId, ownToken).id()).isEqualTo(seatId);
    }
}
