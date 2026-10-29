package com.ticketing.api.domain.concert.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.concert.dto.ConcertResponse;
import com.ticketing.api.domain.concert.entity.Concert;
import com.ticketing.api.domain.concert.repository.ConcertRepository;
import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;
import com.ticketing.api.domain.event.service.EventService;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ConcertServiceImpl 단위 테스트. Repository와 EventService는 Mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class ConcertServiceImplTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private EventService eventService;

    @InjectMocks
    private ConcertServiceImpl concertService;

    // 공연 목록이 비어 있을 때만 기본 공연 10개가 등록되어야 한다
    @Test
    @DisplayName("공연 목록이 비어 있으면 기본 공연 10개를 등록한다")
    @SuppressWarnings("unchecked")
    void initializeCatalog_empty_savesDefaults() {
        // given
        when(concertRepository.count()).thenReturn(0L);
        when(concertRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        concertService.initializeCatalog();

        // then
        ArgumentCaptor<List<Concert>> captor = ArgumentCaptor.forClass(List.class);
        verify(concertRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(10)
                .allSatisfy(concert -> assertThat(concert.getSeatCount()).isBetween(1, EventCreateRequest.MAX_SEAT_COUNT));
    }

    // 이미 공연이 있으면 재시작해도 중복 등록하지 않아야 한다
    @Test
    @DisplayName("공연이 이미 있으면 기본 공연을 다시 등록하지 않는다")
    void initializeCatalog_notEmpty_skips() {
        // given
        when(concertRepository.count()).thenReturn(10L);

        // when
        concertService.initializeCatalog();

        // then
        verify(concertRepository, never()).saveAll(anyList());
    }

    // 공연 목록이 응답 DTO로 변환되어야 한다
    @Test
    @DisplayName("공연 목록을 조회한다")
    void getConcerts_returnsList() {
        // given
        when(concertRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(Concert.create("공연", "출연", "콘서트", "공연장", 100, 50000)));

        // when
        List<ConcertResponse> concerts = concertService.getConcerts();

        // then
        assertThat(concerts).extracting(ConcertResponse::title).containsExactly("공연");
    }

    // 회차를 만들 때 좌석 수는 요청이 아니라 공연 템플릿 값을 써야 한다
    @Test
    @DisplayName("회차를 시작하면 공연 템플릿의 이름과 좌석 수로 이벤트를 만든다")
    void startRun_createsEventFromTemplate() {
        // given
        when(concertRepository.findById(1L))
                .thenReturn(Optional.of(Concert.create("공연", "출연", "콘서트", "공연장", 120, 50000)));
        when(eventService.createEvent(any(EventCreateRequest.class)))
                .thenReturn(new EventResponse(7L, "공연", 120, LocalDateTime.now()));

        // when
        EventResponse run = concertService.startRun(1L);

        // then
        ArgumentCaptor<EventCreateRequest> captor = ArgumentCaptor.forClass(EventCreateRequest.class);
        verify(eventService).createEvent(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("공연");
        assertThat(captor.getValue().seatCount()).isEqualTo(120);
        assertThat(run.id()).isEqualTo(7L);
    }

    // 없는 공연으로 회차를 만들면 이벤트를 만들지 않고 예외가 발생해야 한다
    @Test
    @DisplayName("존재하지 않는 공연으로 회차를 시작하면 CONCERT_NOT_FOUND 예외가 발생한다")
    void startRun_notFound_throws() {
        // given
        when(concertRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> concertService.startRun(99L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONCERT_NOT_FOUND);
        verify(eventService, never()).createEvent(any(EventCreateRequest.class));
    }
}
