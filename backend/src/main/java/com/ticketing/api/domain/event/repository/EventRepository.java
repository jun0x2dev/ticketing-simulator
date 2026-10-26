package com.ticketing.api.domain.event.repository;

import com.ticketing.api.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
}
