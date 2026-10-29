package com.ticketing.api.domain.event.service.impl;

import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;
import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.event.service.EventService;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EventService 구현체. 기본적으로 읽기 전용 트랜잭션을 적용하고,
 * 쓰기 작업(createEvent)에만 별도로 @Transactional을 명시한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private static final Logger log = LoggerFactory.getLogger(EventServiceImpl.class);

    private final EventRepository eventRepository;
    private final SeatService seatService;

    @Override
    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {
        Event event = Event.create(request.name(), request.seatCount(), request.openAt());
        Event saved = eventRepository.save(event);
        // 이벤트 생성 시 좌석 수만큼 좌석을 함께 초기화한다 (1~seatCount번)
        seatService.initializeSeats(saved, saved.getSeatCount());
        log.info("이벤트 생성 완료 - eventId: {}, name: {}", saved.getId(), saved.getName());
        return EventResponse.from(saved);
    }

    @Override
    public EventResponse getEvent(Long eventId) {
        // 존재하지 않는 이벤트 조회 시 404에 해당하는 CustomException으로 변환
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ErrorCode.EVENT_NOT_FOUND));
        return EventResponse.from(event);
    }

    @Override
    @Transactional
    public int deleteRunsOpenedBefore(LocalDateTime openedBefore, int batchSize) {
        List<Long> eventIds = eventRepository.findIdsOpenedBefore(openedBefore, PageRequest.of(0, batchSize));
        if (eventIds.isEmpty()) {
            return 0;
        }
        // 좌석이 회차를 외래키로 참조하므로 좌석을 먼저 지운다. 결제 기록은 좌석과 FK가 없는 이력이라 남겨 둔다.
        int deletedSeats = seatService.deleteSeatsOfEvents(eventIds);
        eventRepository.deleteAllByIdInBatch(eventIds);
        log.info("지난 회차 정리 - events: {}, seats: {}", eventIds.size(), deletedSeats);
        return eventIds.size();
    }
}
