package com.ticketing.api.domain.queue.waiting;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 이벤트별 대기 순번을 Redis Sorted Set으로 관리한다.
 * Kafka 토픽은 "몇 번째인지"를 조회할 수 없으므로, 입장 요청 순서를 따로 기록해 순번 조회에만 쓴다.
 * 키: {@code queue:line:{eventId}} (member = requestId, score = 접수 순번), {@code queue:seq:{eventId}} (접수 순번 카운터)
 */
@Component
@RequiredArgsConstructor
public class WaitingLineManager {

    private static final String LINE_KEY_PREFIX = "queue:line:";
    private static final String SEQUENCE_KEY_PREFIX = "queue:seq:";
    // DLT로 빠지는 등 끝내 허용되지 못한 요청이 줄에 영구히 남지 않도록, 마지막 접수 후 1시간이 지나면 줄 전체를 만료시킨다.
    private static final Duration LINE_TTL = Duration.ofHours(1);
    private static final ScanOptions LINE_KEYS = ScanOptions.scanOptions().match(LINE_KEY_PREFIX + "*").count(100).build();

    private final StringRedisTemplate redisTemplate;

    /**
     * 입장 요청을 줄 맨 뒤에 세운다. 여러 인스턴스에서 접수해도 순서가 꼬이지 않도록 Redis INCR로 순번을 매긴다.
     *
     * @param eventId   이벤트 ID
     * @param requestId 입장 요청 ID
     */
    public void join(Long eventId, String requestId) {
        String sequenceKey = SEQUENCE_KEY_PREFIX + eventId;
        String lineKey = LINE_KEY_PREFIX + eventId;
        Long sequence = redisTemplate.opsForValue().increment(sequenceKey);
        redisTemplate.opsForZSet().add(lineKey, requestId, sequence == null ? 0 : sequence);
        redisTemplate.expire(sequenceKey, LINE_TTL);
        redisTemplate.expire(lineKey, LINE_TTL);
    }

    /**
     * 입장이 허용된 요청을 줄에서 뺀다. 이미 빠져 있어도 문제없다(중복 소비 대비).
     *
     * @param eventId   이벤트 ID
     * @param requestId 입장 요청 ID
     */
    public void leave(Long eventId, String requestId) {
        redisTemplate.opsForZSet().remove(LINE_KEY_PREFIX + eventId, requestId);
    }

    /**
     * 요청의 현재 대기 순번과 뒤에 선 인원을 조회한다.
     *
     * @param eventId   이벤트 ID
     * @param requestId 입장 요청 ID
     * @return 줄에 있으면 위치, 이미 허용되었거나 모르는 요청이면 빈 값
     */
    public Optional<WaitingPosition> findPosition(Long eventId, String requestId) {
        String lineKey = LINE_KEY_PREFIX + eventId;
        Long rank = redisTemplate.opsForZSet().rank(lineKey, requestId);
        if (rank == null) {
            return Optional.empty();
        }
        Long size = redisTemplate.opsForZSet().zCard(lineKey);
        // rank와 zCard는 별도 명령이라 그 사이 줄이 줄어들 수 있으므로 음수가 되지 않게 보정한다.
        long behind = Math.max(0, (size == null ? 0 : size) - rank - 1);
        return Optional.of(new WaitingPosition(rank + 1, behind));
    }

    /**
     * 모든 회차의 줄에 서 있는 인원을 합산한다. 지표 수집 주기마다 불리므로 KEYS 대신 SCAN으로 키를 나눠 읽어
     * Redis를 막지 않는다. 회차 줄은 1시간 TTL로 정리되어 키 수가 크게 늘지 않는다.
     *
     * @return 대기 중인 전체 인원
     */
    public long countAll() {
        long total = 0;
        try (Cursor<String> keys = redisTemplate.scan(LINE_KEYS)) {
            while (keys.hasNext()) {
                Long size = redisTemplate.opsForZSet().zCard(keys.next());
                total += size == null ? 0 : size;
            }
        }
        return total;
    }
}
