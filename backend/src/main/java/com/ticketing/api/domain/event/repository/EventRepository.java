package com.ticketing.api.domain.event.repository;

import com.ticketing.api.domain.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Event 엔티티에 대한 기본 CRUD를 제공하는 레포지토리.
 */
public interface EventRepository extends JpaRepository<Event, Long> {
}
