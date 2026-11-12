package com.ticketing.api.domain.queue.metrics;

import com.ticketing.api.domain.queue.waiting.WaitingLineManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 대기열 상태를 Micrometer 지표로 내보낸다. 지금 줄 선 인원(게이지)과 접수부터 입장 허용까지 실제로 걸린 시간(타이머)을 다룬다.
 */
@Component
public class QueueMetrics {

    private final Timer waitTimer;

    public QueueMetrics(MeterRegistry meterRegistry, WaitingLineManager waitingLineManager) {
        // 대기 인원은 Redis 줄의 크기라 서버가 여러 대여도 모두 같은 값을 낸다. 대시보드에서는 합이 아니라 max로 읽는다.
        Gauge.builder("ticketing.queue.waiting", waitingLineManager, WaitingLineManager::countAll)
                .description("대기열에 줄 선 인원(모든 회차 합계)")
                .register(meterRegistry);
        // 서버 여러 대의 분위수(p95)를 합산할 수 있게 히스토그램 버킷을 내보낸다. 기본 버킷 상한(30초)으로는
        // 몇 분씩 밀리는 대기를 구분하지 못하므로 범위를 대기열에 맞춰 넓힌다.
        this.waitTimer = Timer.builder("ticketing.queue.wait")
                .description("입장 요청 접수부터 입장 허용까지 걸린 시간")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(100))
                .maximumExpectedValue(Duration.ofMinutes(10))
                .register(meterRegistry);
    }

    /**
     * 입장이 허용된 요청의 실제 대기 시간을 기록한다. DLT 재투입을 거친 요청도 처음 접수한 시각부터 잰다.
     *
     * @param requestedAt 입장 요청을 처음 접수한 시각
     */
    public void recordWait(Instant requestedAt) {
        // 접수한 서버와 허용한 서버의 시계가 조금 어긋나 음수가 나와도 0으로 기록한다.
        Duration waited = Duration.between(requestedAt, Instant.now());
        waitTimer.record(waited.isNegative() ? Duration.ZERO : waited);
    }
}
