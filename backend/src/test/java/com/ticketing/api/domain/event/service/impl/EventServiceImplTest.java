package com.ticketing.api.domain.event.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;
import com.ticketing.api.domain.event.entity.Event;
import com.ticketing.api.domain.event.repository.EventRepository;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * EventServiceImpl 단위 테스트. Repository는 Mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SeatService seatService;

    @InjectMocks
    private EventServiceImpl eventService;

    // 정상적인 요청으로 이벤트를 생성하면 저장된 값 그대로 응답에 반영되는지 확인
    @Test
    @DisplayName("이벤트를 생성하면 저장된 정보를 응답으로 반환한다")
    void createEvent_success() {
        // given
        EventCreateRequest request = new EventCreateRequest("콘서트 A", 100, LocalDateTime.now().plusDays(1));
        Event savedEvent = Event.create(request.name(), request.seatCount(), request.openAt());
        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        // when
        EventResponse response = eventService.createEvent(request);

        // then
        assertThat(response.name()).isEqualTo("콘서트 A");
        assertThat(response.seatCount()).isEqualTo(100);
    }

    // 존재하는 이벤트를 ID로 조회하면 정상적으로 응답이 반환되는지 확인
    @Test
    @DisplayName("존재하는 이벤트를 조회하면 해당 이벤트 정보를 반환한다")
    void getEvent_success() {
        // given
        Event event = Event.create("콘서트 A", 100, LocalDateTime.now().plusDays(1));
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        // when
        EventResponse response = eventService.getEvent(1L);

        // then
        assertThat(response.name()).isEqualTo("콘서트 A");
    }

    // 존재하지 않는 ID로 조회하면 EVENT_NOT_FOUND CustomException이 발생하는지 확인
    @Test
    @DisplayName("존재하지 않는 이벤트를 조회하면 예외가 발생한다")
    void getEvent_notFound() {
        // given
        when(eventRepository.findById(999L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> eventService.getEvent(999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }
}
