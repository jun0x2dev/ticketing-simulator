package com.ticketing.api.domain.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 티케팅 대상이 되는 공연/행사 정보를 나타내는 엔티티.
 * 좌석 수와 오픈 시각을 가지며, 상태 전이는 없는 단순 메타데이터다.
 */
@Entity
// 지난 회차 정리 작업이 오픈 시각으로 대상을 찾으므로 인덱스를 둔다.
@Table(name = "events", indexes = @Index(name = "idx_events_open_at", columnList = "open_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int seatCount;

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt;

    private Event(String name, int seatCount, LocalDateTime openAt) {
        this.name = name;
        this.seatCount = seatCount;
        this.openAt = openAt;
    }

    /**
     * 이벤트를 생성하는 정적 팩토리 메서드.
     *
     * @param name      이벤트명
     * @param seatCount 전체 좌석 수
     * @param openAt    티케팅 오픈 시각
     * @return 생성된 Event 인스턴스
     */
    public static Event create(String name, int seatCount, LocalDateTime openAt) {
        return new Event(name, seatCount, openAt);
    }
}
