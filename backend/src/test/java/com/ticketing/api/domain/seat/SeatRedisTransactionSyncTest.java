package com.ticketing.api.domain.seat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.scheduler.SeatHoldExpirationScheduler;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 좌석 DB 변경이 커밋되지 못했을 때 Redis 락/토큰 표시가 DB와 어긋나지 않는지 검증한다.
 * 커밋 실패는 서비스 메서드를 바깥 트랜잭션에서 호출한 뒤 롤백시켜 재현한다.
 */
@SpringBootTest
@Testcontainers
class SeatRedisTransactionSyncTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // 테스트 도중 TTL 복구가 좌석 상태를 바꾸지 않도록 스케줄러를 대체한다
    @MockitoBean
    private SeatHoldExpirationScheduler seatHoldExpirationScheduler;

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EntryTokenManager entryTokenManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Event event;
    private Long seatId;

    @BeforeEach
    void setUp() {
        seatRepository.deleteAll();
        eventRepository.deleteAll();
        event = eventRepository.save(Event.create("콘서트 커밋 실패 테스트", 2, LocalDateTime.now().plusDays(1)));
        seatId = seatRepository.save(Seat.create(event, 1)).getId();
    }

    // 선점이 커밋되지 못하면 좌석 락과 토큰 표시가 바로 풀려, 좌석과 사용자가 5분 동안 묶이지 않아야 한다
    @Test
    @DisplayName("선점 트랜잭션이 롤백되면 좌석 락과 토큰 표시가 바로 풀린다")
    void holdSeat_rolledBack_releasesLockAndHolderMark() {
        // given
        String token = issueToken();
        String otherToken = issueToken();
        Long secondSeatId = seatRepository.save(Seat.create(event, 2)).getId();

        // when
        transactionTemplate.executeWithoutResult(status -> {
            seatService.holdSeat(seatId, token);
            status.setRollbackOnly();
        });

        // then
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatService.holdSeat(seatId, otherToken).id()).isEqualTo(seatId);
        assertThat(seatService.holdSeat(secondSeatId, token).id()).isEqualTo(secondSeatId);
    }

    // 확정이 커밋되지 못하면 좌석은 HELD로 남으므로, 토큰 표시도 남아 같은 토큰으로 다른 좌석을 더 잡을 수 없어야 한다
    @Test
    @DisplayName("확정 트랜잭션이 롤백되면 토큰 표시가 남아 다른 좌석을 추가로 잡을 수 없다")
    void confirmSeat_rolledBack_keepsHolderMark() {
        // given
        String token = issueToken();
        Long secondSeatId = seatRepository.save(Seat.create(event, 2)).getId();
        seatService.holdSeat(seatId, token);

        // when
        transactionTemplate.executeWithoutResult(status -> {
            seatService.confirmSeat(seatId, token);
            status.setRollbackOnly();
        });

        // then
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        assertThatThrownBy(() -> seatService.holdSeat(secondSeatId, token))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_HOLD_LIMIT_EXCEEDED);
    }

    // 해제가 커밋되지 못하면 좌석은 HELD로 남으므로, 좌석 락도 남아 다른 사용자가 선점할 수 없어야 한다
    @Test
    @DisplayName("해제 트랜잭션이 롤백되면 좌석 락이 남아 다른 사용자가 선점할 수 없다")
    void releaseSeat_rolledBack_keepsSeatLock() {
        // given
        String token = issueToken();
        String otherToken = issueToken();
        seatService.holdSeat(seatId, token);

        // when
        transactionTemplate.executeWithoutResult(status -> {
            seatService.releaseSeat(seatId, token);
            status.setRollbackOnly();
        });

        // then
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        assertThatThrownBy(() -> seatService.holdSeat(seatId, otherToken))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEAT_ALREADY_HELD);
    }

    private String issueToken() {
        return entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();
    }
}
