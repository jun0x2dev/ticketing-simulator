package com.ticketing.api.domain.queue.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketing.api.domain.event.service.EventService;
import com.ticketing.api.domain.queue.dto.EntryGrantedEvent;
import com.ticketing.api.domain.queue.dto.EntryRequestResponse;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.dto.EntryStatus;
import com.ticketing.api.domain.queue.dto.EntryStatusResponse;
import com.ticketing.api.domain.queue.producer.EntryGrantedProducer;
import com.ticketing.api.domain.queue.producer.EntryRequestProducer;
import com.ticketing.api.domain.queue.token.EntryTokenManager;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * EntryServiceImpl 단위 테스트. Redis 토큰 관리자와 Kafka Producer는 Mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class EntryServiceImplTest {

    @Mock
    private EventService eventService;

    @Mock
    private EntryRequestProducer entryRequestProducer;

    @Mock
    private EntryGrantedProducer entryGrantedProducer;

    @Mock
    private EntryTokenManager entryTokenManager;

    @InjectMocks
    private EntryServiceImpl entryService;

    // 존재하는 이벤트에 입장 요청을 하면 대기열에 적재되고 요청 ID가 반환되는지 확인
    @Test
    @DisplayName("입장 요청을 접수하면 entry.requested를 발행하고 requestId를 반환한다")
    void requestEntry_success() {
        // when
        EntryRequestResponse response = entryService.requestEntry(1L);

        // then
        assertThat(response.requestId()).isNotBlank();
        verify(entryRequestProducer).send(any(EntryRequestedEvent.class));
    }

    // 존재하지 않는 이벤트에 대한 입장 요청은 대기열에 적재되기 전에 거절되어야 한다
    @Test
    @DisplayName("존재하지 않는 이벤트에 입장 요청하면 예외가 발생하고 발행하지 않는다")
    void requestEntry_eventNotFound_throws() {
        // given
        when(eventService.getEvent(99L)).thenThrow(new CustomException(ErrorCode.EVENT_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> entryService.requestEntry(99L)).isInstanceOf(CustomException.class);
        verify(entryRequestProducer, never()).send(any(EntryRequestedEvent.class));
    }

    // 처음 처리하는 입장 요청은 토큰이 발급되고 entry.granted가 발행되어야 한다
    @Test
    @DisplayName("처음 소비한 입장 요청은 토큰을 발급하고 entry.granted를 발행한다")
    void grantEntry_firstTime_issuesTokenAndPublishes() {
        // given
        EntryRequestedEvent event = EntryRequestedEvent.of(1L);
        when(entryTokenManager.issue(event.requestId(), 1L)).thenReturn(Optional.of("token"));

        // when
        entryService.grantEntry(event);

        // then
        verify(entryGrantedProducer).send(any(EntryGrantedEvent.class));
    }

    // 같은 요청이 재전달되어도 토큰/이벤트가 중복 발행되면 안 된다 (멱등성)
    @Test
    @DisplayName("이미 처리된 입장 요청이 다시 소비되면 아무것도 발행하지 않는다")
    void grantEntry_duplicate_isIgnored() {
        // given
        EntryRequestedEvent event = EntryRequestedEvent.of(1L);
        when(entryTokenManager.issue(event.requestId(), 1L)).thenReturn(Optional.empty());

        // when
        entryService.grantEntry(event);

        // then
        verify(entryGrantedProducer, never()).send(any(EntryGrantedEvent.class));
    }

    // 토큰이 발급된 요청은 GRANTED와 토큰을, 아직 발급 전이면 WAITING을 반환해야 한다
    @Test
    @DisplayName("토큰 발급 여부에 따라 GRANTED/WAITING 상태를 반환한다")
    void getEntryStatus_grantedAndWaiting() {
        // given
        when(entryTokenManager.findToken("granted-id")).thenReturn(Optional.of("token"));
        when(entryTokenManager.findToken("waiting-id")).thenReturn(Optional.empty());

        // when
        EntryStatusResponse granted = entryService.getEntryStatus("granted-id");
        EntryStatusResponse waiting = entryService.getEntryStatus("waiting-id");

        // then
        assertThat(granted.status()).isEqualTo(EntryStatus.GRANTED);
        assertThat(granted.token()).isEqualTo("token");
        assertThat(waiting.status()).isEqualTo(EntryStatus.WAITING);
        assertThat(waiting.token()).isNull();
    }
}
