package com.ticketing.api.domain.seat.lock;

import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 좌석 단위 Redis 분산 락을 관리한다. 같은 좌석에 대한 동시 선점 요청을
 * {@code SET key value NX PX ttl} 1회 명령(원자적)으로 막는다.
 * 락 값에 UUID를 넣어서, 락을 획득한 요청만 자신의 락을 해제할 수 있도록 한다
 * (다른 요청이 TTL 만료 후 새로 획득한 락을 실수로 해제하는 것을 방지).
 */
@Component
@RequiredArgsConstructor
public class SeatLockManager {

    private static final Logger log = LoggerFactory.getLogger(SeatLockManager.class);
    private static final String LOCK_KEY_PREFIX = "seat:lock:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 좌석 락 획득을 시도한다.
     *
     * @param seatId     락을 걸 좌석 ID
     * @param ttl        락 유지 시간 (결제 제한시간과 동일하게 맞춰 사용)
     * @return 락 획득에 성공하면 해제 시 필요한 토큰, 실패하면 null
     */
    public String tryLock(Long seatId, Duration ttl) {
        String key = lockKey(seatId);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return token;
        }
        return null;
    }

    /**
     * 좌석 락을 해제한다. 저장된 토큰과 일치할 때만 삭제해서,
     * 이미 TTL이 만료되어 다른 요청이 새로 획득한 락을 잘못 지우지 않도록 한다.
     *
     * @param seatId 락을 해제할 좌석 ID
     * @param token  락 획득 시 받은 토큰
     */
    public void unlock(Long seatId, String token) {
        String key = lockKey(seatId);
        String currentValue = redisTemplate.opsForValue().get(key);
        if (token.equals(currentValue)) {
            redisTemplate.delete(key);
        } else {
            log.warn("좌석 락 해제 스킵 - 토큰 불일치 또는 이미 만료됨. seatId: {}", seatId);
        }
    }

    /**
     * 토큰 검증 없이 좌석 락을 강제로 해제한다. 결제 실패/TTL 만료 등 관리 목적의
     * 좌석 해제 플로우에서 사용한다 (holder의 토큰을 알 수 없는 경우).
     *
     * @param seatId 락을 해제할 좌석 ID
     */
    public void forceUnlock(Long seatId) {
        redisTemplate.delete(lockKey(seatId));
    }

    private String lockKey(Long seatId) {
        return LOCK_KEY_PREFIX + seatId;
    }
}
