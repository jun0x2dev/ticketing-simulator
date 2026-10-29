package com.ticketing.api.domain.payment.gateway;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실제 PG 연동 없이 결제 결과를 흉내 내는 Mock 게이트웨이.
 * 임의의 지연 후 설정된 확률로 승인/거절을 반환하고, 승인 건은 승인 번호로 취소할 수 있다.
 */
@Component
public class MockPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

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
     * @return 승인 결과 (승인이면 승인 번호 포함)
     */
    public ChargeResult charge() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            Thread.sleep(random.nextLong(minDelayMillis, maxDelayMillis + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ChargeResult.declined();
        }
        return random.nextDouble() < successRate
                ? ChargeResult.approved(UUID.randomUUID().toString())
                : ChargeResult.declined();
    }

    /**
     * 승인된 결제를 취소한다. 승인 후 좌석 확정에 실패했을 때 돈만 빠져나가지 않도록 호출한다.
     *
     * @param approvalId 취소할 승인 번호
     */
    public void cancel(String approvalId) {
        log.info("결제 승인 취소 - approvalId: {}", approvalId);
    }
}
