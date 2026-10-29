package com.ticketing.api.domain.stats.recorder;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 회차별 통계 카운터를 Redis Hash({@code stats:event:{eventId}})에 기록한다.
 * Kafka는 같은 메시지를 다시 전달할 수 있으므로, 메시지마다 고유 키로 처리 여부를 남겨 한 번만 센다.
 */
@Component
@RequiredArgsConstructor
public class EventStatsRecorder {

    private static final String STATS_KEY_PREFIX = "stats:event:";
    private static final String DEDUP_KEY_PREFIX = "stats:dedup:";
    // 회차는 24시간 뒤 정리되므로, 통계와 중복 확인 키도 그보다 넉넉히 2일 뒤 만료시킨다.
    private static final Duration TTL = Duration.ofDays(2);

    // 중복 확인(SET NX)과 카운터 증가(HINCRBY)를 한 스크립트로 묶어, 둘 사이에 실패해 한쪽만 반영되는 일을 막는다.
    private static final RedisScript<Long> INCREMENT_ONCE = new DefaultRedisScript<>("""
            if redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[2]) then
              redis.call('HINCRBY', KEYS[2], ARGV[1], 1)
              redis.call('EXPIRE', KEYS[2], ARGV[2])
              return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 회차의 통계 항목을 1 증가시킨다. 같은 dedupKey로는 한 번만 반영된다.
     *
     * @param eventId  회차(이벤트) ID
     * @param field    증가시킬 항목 이름
     * @param dedupKey 메시지를 식별하는 고유 키
     * @return 이번에 반영되었으면 true, 이미 반영된 메시지면 false
     */
    public boolean incrementOnce(Long eventId, String field, String dedupKey) {
        Long applied = redisTemplate.execute(INCREMENT_ONCE,
                List.of(DEDUP_KEY_PREFIX + dedupKey, STATS_KEY_PREFIX + eventId),
                field, String.valueOf(TTL.toSeconds()));
        return Long.valueOf(1).equals(applied);
    }

    /**
     * 회차의 통계 항목을 모두 조회한다.
     *
     * @param eventId 회차(이벤트) ID
     * @return 항목 이름 → 값 (기록이 없으면 빈 맵)
     */
    public Map<String, Long> getAll(Long eventId) {
        return redisTemplate.<String, String>opsForHash().entries(STATS_KEY_PREFIX + eventId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Long.parseLong(entry.getValue())));
    }
}
