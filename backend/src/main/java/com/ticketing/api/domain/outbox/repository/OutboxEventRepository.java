package com.ticketing.api.domain.outbox.repository;

import com.ticketing.api.domain.outbox.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * OutboxEvent 저장과 발행 대기/발행 완료 이벤트 조회를 제공하는 레포지토리.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 발행 대기 이벤트를 기록된 순서대로 행 잠금(FOR UPDATE SKIP LOCKED)으로 가져온다.
     * 서버가 여러 대여도 다른 인스턴스가 잡은 행은 건너뛰므로, 같은 이벤트를 동시에 발행하지 않는다.
     *
     * @param pageable 한 번에 가져올 개수
     * @return 발행 대기 이벤트 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // 잠금 대기 시간 -2는 Hibernate에서 SKIP LOCKED를 뜻한다.
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from OutboxEvent o where o.publishedAt is null order by o.id")
    List<OutboxEvent> findPendingForUpdate(Pageable pageable);

    /**
     * 기준 시각 이전에 발행 완료된 이벤트 ID를 오래된 순으로 조회한다. 정리 작업을 나눠 처리하기 위해 페이지 단위로 읽는다.
     *
     * @param publishedBefore 이 시각 이전에 발행된 이벤트가 대상
     * @param pageable        한 번에 읽을 개수
     * @return 이벤트 ID 목록
     */
    @Query("select o.id from OutboxEvent o where o.publishedAt < :publishedBefore order by o.id")
    List<Long> findIdsPublishedBefore(@Param("publishedBefore") LocalDateTime publishedBefore, Pageable pageable);
}
