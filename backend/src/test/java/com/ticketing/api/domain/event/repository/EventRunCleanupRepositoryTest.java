package com.ticketing.api.domain.event.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.seat.entity.Seat;
import com.ticketing.api.domain.seat.repository.SeatRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/**
 * 지난 회차 정리에 쓰는 쿼리(오래된 회차 ID 조회, 회차별 좌석 일괄 삭제) 슬라이스 테스트.
 */
@DataJpaTest
class EventRunCleanupRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SeatRepository seatRepository;

    // 기준 시각 이전에 열린 회차만 오래된 순으로, 요청한 개수만큼 조회되어야 한다
    @Test
    @DisplayName("기준 시각 이전에 열린 회차 ID만 오래된 순으로 조회한다")
    void findIdsOpenedBefore_returnsOnlyOldRuns() {
        // given
        LocalDateTime now = LocalDateTime.now();
        Event old1 = eventRepository.save(Event.create("지난 회차 1", 1, now.minusDays(3)));
        Event old2 = eventRepository.save(Event.create("지난 회차 2", 1, now.minusDays(2)));
        eventRepository.save(Event.create("진행 중 회차", 1, now.minusMinutes(5)));

        // when
        List<Long> all = eventRepository.findIdsOpenedBefore(now.minusDays(1), PageRequest.of(0, 10));
        List<Long> firstOnly = eventRepository.findIdsOpenedBefore(now.minusDays(1), PageRequest.of(0, 1));

        // then
        assertThat(all).containsExactly(old1.getId(), old2.getId());
        assertThat(firstOnly).containsExactly(old1.getId());
    }

    // 지정한 회차의 좌석만 지우고 다른 회차 좌석은 남겨야 한다
    @Test
    @DisplayName("지정한 회차의 좌석만 일괄 삭제한다")
    void deleteByEventIds_deletesOnlyTargetSeats() {
        // given
        Event target = eventRepository.save(Event.create("지울 회차", 2, LocalDateTime.now().minusDays(2)));
        Event kept = eventRepository.save(Event.create("남길 회차", 1, LocalDateTime.now()));
        seatRepository.saveAll(List.of(Seat.create(target, 1), Seat.create(target, 2), Seat.create(kept, 1)));

        // when
        int deleted = seatRepository.deleteByEventIds(List.of(target.getId()));

        // then
        assertThat(deleted).isEqualTo(2);
        assertThat(seatRepository.findByEventIdOrderBySeatNumberAsc(target.getId())).isEmpty();
        assertThat(seatRepository.findByEventIdOrderBySeatNumberAsc(kept.getId())).hasSize(1);
    }
}
