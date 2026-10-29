package com.ticketing.api.domain.event.repository;

import com.ticketing.api.domain.event.entity.Event;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Event 엔티티에 대한 기본 CRUD를 제공하는 레포지토리.
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * 오픈 시각이 기준 시각보다 이전인 회차 ID를 오래된 순으로 조회한다. 정리 작업을 나눠 처리하기 위해 페이지 단위로 읽는다.
     *
     * @param openedBefore 이 시각 이전에 열린 회차가 대상
     * @param pageable     한 번에 읽을 개수
     * @return 회차 ID 목록
     */
    @Query("select e.id from Event e where e.openAt < :openedBefore order by e.id")
    List<Long> findIdsOpenedBefore(@Param("openedBefore") LocalDateTime openedBefore, Pageable pageable);
}
