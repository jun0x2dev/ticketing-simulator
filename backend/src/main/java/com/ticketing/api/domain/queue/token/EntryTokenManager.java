package com.ticketing.api.domain.queue.token;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 입장 허용 토큰을 Redis에 발급/조회한다. 키는 두 가지다.
 * {@code entry:grant:{requestId}} → 토큰 (요청당 1회 발급 보장, 멱등성 키 역할)
 * {@code entry:token:{token}} → eventId (좌석 선점 시 토큰 검증용)
 * 재처리까지 실패한 요청은 {@code entry:failed:{requestId}}로 표시한다.
 * 두 키 모두 TTL이 있어서 만료된 토큰은 자동으로 무효화된다.
 */
@Component
public class EntryTokenManager {

    private static final String GRANT_KEY_PREFIX = "entry:grant:";
    private static final String TOKEN_KEY_PREFIX = "entry:token:";
    private static final String FAILED_KEY_PREFIX = "entry:failed:";

    private final StringRedisTemplate redisTemplate;
    private final Duration tokenTtl;

    public EntryTokenManager(StringRedisTemplate redisTemplate,
                             @Value("${queue.token-ttl-seconds:600}") long tokenTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.tokenTtl = Duration.ofSeconds(tokenTtlSeconds);
    }

    /**
     * 입장 요청에 대해 토큰을 발급한다. 같은 requestId로 이미 발급된 경우(Kafka 재전달 등) 발급하지 않는다.
     *
     * @param requestId 입장 요청 ID
     * @param eventId   입장 대상 이벤트 ID
     * @return 새로 발급한 토큰, 이미 발급된 요청이면 빈 값
     */
    public Optional<String> issue(String requestId, Long eventId) {
        String token = UUID.randomUUID().toString();
        // 토큰 키를 먼저 쓰고 grant 키를 NX로 선점한다. 중간에 죽어도 참조되지 않는 토큰이 TTL로 사라질 뿐이다.
        redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, String.valueOf(eventId), tokenTtl);
        Boolean first = redisTemplate.opsForValue().setIfAbsent(GRANT_KEY_PREFIX + requestId, token, tokenTtl);
        if (Boolean.TRUE.equals(first)) {
            return Optional.of(token);
        }
        redisTemplate.delete(TOKEN_KEY_PREFIX + token);
        return Optional.empty();
    }

    /**
     * 요청 ID로 발급된 토큰을 조회한다.
     *
     * @param requestId 입장 요청 ID
     * @return 발급된 토큰, 아직 없거나 만료되었으면 빈 값
     */
    public Optional<String> findToken(String requestId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(GRANT_KEY_PREFIX + requestId));
    }

    /**
     * 토큰이 입장을 허용받은 이벤트 ID를 조회한다.
     *
     * @param token 입장 토큰 (null/공백이면 빈 값)
     * @return 토큰에 묶인 이벤트 ID, 없거나 만료되었으면 빈 값
     */
    public Optional<Long> findEventId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token))
                .map(Long::valueOf);
    }

    /**
     * 재처리까지 실패한 입장 요청을 표시한다. 토큰과 같은 TTL로 남겨 두어 클라이언트가 실패를 확인할 수 있게 한다.
     *
     * @param requestId 입장 요청 ID
     */
    public void markFailed(String requestId) {
        redisTemplate.opsForValue().set(FAILED_KEY_PREFIX + requestId, "1", tokenTtl);
    }

    /**
     * 입장 요청이 최종 실패로 표시되었는지 확인한다.
     *
     * @param requestId 입장 요청 ID
     * @return 실패로 표시되었으면 true
     */
    public boolean isFailed(String requestId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(FAILED_KEY_PREFIX + requestId));
    }
}
