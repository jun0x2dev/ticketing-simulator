package com.ticketing.api.domain.seat.repository;

import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.entity.SeatStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 선점 시각이 기준 시각보다 이전인 HELD 좌석을 한 번의 UPDATE로 AVAILABLE로 되돌린다.
     * 조건에 상태를 포함해서, 스캔 중 결제 확정(CONFIRMED)된 좌석은 건드리지 않는다.
     *
     * @param heldBefore 이 시각 이전에 선점된 좌석이 대상
     * @return 복구된 좌석 수
     */
    @Modifying(clearAutomatically = true)
    @Query("update Seat s set s.status = com.ticketing.api.domain.seat.entity.SeatStatus.AVAILABLE, s.heldAt = null "
            + "where s.status = com.ticketing.api.domain.seat.entity.SeatStatus.HELD and s.heldAt < :heldBefore")
    int releaseExpiredHeldSeats(@Param("heldBefore") LocalDateTime heldBefore);
}
