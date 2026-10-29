package com.ticketing.api.domain.seat.repository;

import com.ticketing.api.domain.seat.entity.Seat;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * 선점 시각이 기준 시각보다 이전인 HELD 좌석을 행 잠금(FOR UPDATE)으로 조회한다.
     * 잠금 덕분에 조회~복구 사이에 결제 확정(CONFIRMED)이 끼어들어도 확정된 좌석을 되돌리지 않는다.
     *
     * @param heldBefore 이 시각 이전에 선점된 좌석이 대상
     * @return 만료된 HELD 좌석 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.status = com.ticketing.api.domain.seat.entity.SeatStatus.HELD "
            + "and s.heldAt < :heldBefore")
    List<Seat> findExpiredHeldSeatsForUpdate(@Param("heldBefore") LocalDateTime heldBefore);

    /**
     * 여러 회차의 좌석을 한 번의 DELETE로 지운다. 회차 정리 시 회차보다 먼저 호출해야 한다(좌석이 회차를 참조).
     *
     * @param eventIds 좌석을 지울 회차 ID 목록
     * @return 삭제된 좌석 수
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from Seat s where s.event.id in :eventIds")
    int deleteByEventIds(@Param("eventIds") List<Long> eventIds);
}
