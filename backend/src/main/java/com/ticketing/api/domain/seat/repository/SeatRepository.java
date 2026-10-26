package com.ticketing.api.domain.seat.repository;

import com.ticketing.api.domain.seat.entity.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Seat 엔티티에 대한 기본 CRUD 및 이벤트별 조회를 제공하는 레포지토리.
 */
public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * 특정 이벤트에 속한 모든 좌석을 좌석 번호 순으로 조회한다.
     *
     * @param eventId 이벤트 ID
     * @return 좌석 목록
     */
    List<Seat> findByEventIdOrderBySeatNumberAsc(Long eventId);
}
