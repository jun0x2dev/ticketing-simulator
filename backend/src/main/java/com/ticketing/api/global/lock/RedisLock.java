package com.ticketing.api.global.lock;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 키 하나로 거는 분산 락. {@code SET key token NX PX ttl} 한 번으로 획득하고,
 * 획득할 때 받은 토큰과 일치할 때만 해제한다. TTL이 있어 해제하지 못해도 영구히 잠기지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RedisLock {

    // 조회와 삭제를 따로 하면, 그 사이 TTL이 만료되고 다른 요청이 새로 잡은 락을 지울 수 있어 한 스크립트로 묶는다.
    private static final RedisScript<Long> UNLOCK_IF_OWNER = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 락 획득을 시도한다.
     *
     * @param key 락 키
     * @param ttl 락 유지 시간
     * @return 획득하면 해제에 필요한 토큰, 이미 잠겨 있으면 null
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, ttl)) ? token : null;
    }

    /**
     * 내가 잡은 락일 때만 해제한다.
     *
     * @param key   락 키
     * @param token 획득할 때 받은 토큰
     * @return 해제했으면 true, 이미 만료되었거나 다른 요청의 락이면 false
     */
    public boolean unlock(String key, String token) {
        Long deleted = redisTemplate.execute(UNLOCK_IF_OWNER, List.of(key), token);
        return Long.valueOf(1).equals(deleted);
    }
}
