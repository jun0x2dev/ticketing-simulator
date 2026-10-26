package com.ticketing.api.domain.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "events")
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

    @Column(nullable = false)
    private LocalDateTime openAt;

    private Event(String name, int seatCount, LocalDateTime openAt) {
        this.name = name;
        this.seatCount = seatCount;
        this.openAt = openAt;
    }

    public static Event create(String name, int seatCount, LocalDateTime openAt) {
        return new Event(name, seatCount, openAt);
    }
}
