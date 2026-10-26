package com.ticketing.api.domain.payment.gateway;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실제 PG 연동 없이 결제 결과를 흉내 내는 Mock 게이트웨이.
 * 임의의 지연 후 설정된 확률로 승인/거절을 반환한다.
 */
@Component
public class MockPaymentGateway {

    private final double successRate;
    private final long minDelayMillis;
    private final long maxDelayMillis;

    public MockPaymentGateway(
            @Value("${payment.mock.success-rate:0.8}") double successRate,
            @Value("${payment.mock.min-delay-ms:200}") long minDelayMillis,
            @Value("${payment.mock.max-delay-ms:1500}") long maxDelayMillis) {
        this.successRate = successRate;
        this.minDelayMillis = minDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
    }

    /**
     * 결제를 시도한다. 호출 스레드를 지연시간만큼 블로킹하므로 트랜잭션 밖에서 호출해야 한다.
     *
     * @return 승인되면 true, 거절되면 false
     */
    public boolean charge() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            Thread.sleep(random.nextLong(minDelayMillis, maxDelayMillis + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return random.nextDouble() < successRate;
    }
}
