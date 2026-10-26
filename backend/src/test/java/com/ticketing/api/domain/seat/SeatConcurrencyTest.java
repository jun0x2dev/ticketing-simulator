package com.ticketing.api.domain.seat;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
 * 같은 좌석에 다수의 동시 요청이 들어왔을 때, Redis 분산 락으로
 * 단 하나의 요청만 선점에 성공하는지 검증하는 동시성 테스트.
 * 실제 Redis 컨테이너(Testcontainers)를 사용해서 SETNX의 원자성을 그대로 검증한다.
 */
@SpringBootTest
@Testcontainers
class SeatConcurrencyTest {

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
        Event event = eventRepository.save(Event.create("콘서트 동시성 테스트", 1, LocalDateTime.now().plusDays(1)));
        entryToken = entryTokenManager.issue(UUID.randomUUID().toString(), event.getId()).orElseThrow();
        Seat seat = seatRepository.save(Seat.create(event, 1));
        seatId = seat.getId();
    }

    // 같은 좌석에 50개의 요청이 동시에 들어와도 정확히 1건만 선점에 성공해야 한다
    @Test
    @DisplayName("동시에 50개의 선점 요청이 와도 단 1건만 성공한다")
    void holdSeat_concurrentRequests_onlyOneSucceeds() throws InterruptedException {
        // given
        int requestCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    seatService.holdSeat(seatId, entryToken);
                    successCount.incrementAndGet();
                } catch (CustomException e) {
                    conflictCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(requestCount - 1);

        List<Seat> seats = seatRepository.findAll();
        assertThat(seats).hasSize(1);
        assertThat(seats.get(0).getStatus()).isEqualTo(SeatStatus.HELD);
    }
}
