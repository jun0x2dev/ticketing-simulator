package com.ticketing.api.domain.queue.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import com.ticketing.api.domain.queue.waiting.WaitingLineManager;
import com.ticketing.api.domain.queue.waiting.WaitingPosition;
import com.ticketing.api.global.exception.CustomException;
import com.ticketing.api.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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

    @Mock
    private WaitingLineManager waitingLineManager;

    private EntryServiceImpl entryService;

    @BeforeEach
    void setUp() {
        // 예상 대기 시간 계산을 검증하기 위해 큐 워커 처리량을 초당 20명으로 고정한다
        entryService = new EntryServiceImpl(eventService, entryRequestProducer, entryGrantedProducer,
                entryTokenManager, waitingLineManager, 20);
    }

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

    // Consumer가 먼저 처리해 줄에 남는 요청이 없도록, 줄 세우기가 Kafka 발행보다 먼저여야 한다
    @Test
    @DisplayName("입장 요청은 대기 줄에 먼저 세운 뒤 entry.requested를 발행한다")
    void requestEntry_joinsLineBeforePublishing() {
        // when
        EntryRequestResponse response = entryService.requestEntry(1L);

        // then
        InOrder inOrder = inOrder(waitingLineManager, entryRequestProducer);
        inOrder.verify(waitingLineManager).join(1L, response.requestId());
        inOrder.verify(entryRequestProducer).send(any(EntryRequestedEvent.class));
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
        verify(waitingLineManager).leave(1L, event.requestId());
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

    // 대기열 없이 직접 입장하면 Kafka 발행 없이 즉시 토큰이 담긴 GRANTED가 반환되어야 한다
    @Test
    @DisplayName("직접 입장 시 대기열 발행 없이 즉시 토큰을 발급한다")
    void grantEntryDirectly_issuesTokenWithoutQueue() {
        // given
        when(entryTokenManager.issue(any(String.class), eq(1L))).thenReturn(Optional.of("token"));

        // when
        EntryStatusResponse response = entryService.grantEntryDirectly(1L);

        // then
        assertThat(response.status()).isEqualTo(EntryStatus.GRANTED);
        assertThat(response.token()).isEqualTo("token");
        verify(entryRequestProducer, never()).send(any(EntryRequestedEvent.class));
    }

    // 토큰이 발급된 요청은 순번 없이 GRANTED와 토큰을 반환해야 한다
    @Test
    @DisplayName("토큰이 발급된 요청은 GRANTED와 토큰을 반환한다")
    void getEntryStatus_granted() {
        // given
        when(entryTokenManager.findToken("granted-id")).thenReturn(Optional.of("token"));

        // when
        EntryStatusResponse granted = entryService.getEntryStatus(1L, "granted-id");

        // then
        assertThat(granted.status()).isEqualTo(EntryStatus.GRANTED);
        assertThat(granted.token()).isEqualTo("token");
        assertThat(granted.position()).isNull();
    }

    // 대기 중이면 순번/뒤 인원과 함께, 처리량 기준 예상 대기 시간을 올림으로 계산해야 한다
    @Test
    @DisplayName("대기 중인 요청은 순번과 예상 대기 시간을 반환한다")
    void getEntryStatus_waitingWithPosition() {
        // given
        when(entryTokenManager.findToken("waiting-id")).thenReturn(Optional.empty());
        when(waitingLineManager.findPosition(1L, "waiting-id")).thenReturn(Optional.of(new WaitingPosition(45, 120)));

        // when
        EntryStatusResponse waiting = entryService.getEntryStatus(1L, "waiting-id");

        // then
        assertThat(waiting.status()).isEqualTo(EntryStatus.WAITING);
        assertThat(waiting.token()).isNull();
        assertThat(waiting.position()).isEqualTo(45);
        assertThat(waiting.behind()).isEqualTo(120);
        assertThat(waiting.estimatedSeconds()).isEqualTo(3); // 45명 / 초당 20명 = 2.25초 → 3초
    }

    // 재처리까지 실패로 표시된 요청은 FAILED여야 한다
    @Test
    @DisplayName("최종 실패로 표시된 요청은 FAILED를 반환한다")
    void getEntryStatus_failed() {
        // given
        when(entryTokenManager.findToken("failed-id")).thenReturn(Optional.empty());
        when(entryTokenManager.isFailed("failed-id")).thenReturn(true);

        // when
        EntryStatusResponse failed = entryService.getEntryStatus(1L, "failed-id");

        // then
        assertThat(failed.status()).isEqualTo(EntryStatus.FAILED);
        assertThat(failed.position()).isNull();
    }

    // DLT에서 처음 꺼낸 요청은 재투입 횟수를 1 올려 원래 토픽으로 다시 보내야 한다
    @Test
    @DisplayName("DLT 요청에 재투입 횟수가 남아 있으면 원래 토픽으로 다시 보낸다")
    void handleDeadLetter_redrivesOnce() {
        // given
        EntryRequestedEvent event = EntryRequestedEvent.of(1L);

        // when
        entryService.handleDeadLetter(event);

        // then
        ArgumentCaptor<EntryRequestedEvent> captor = ArgumentCaptor.forClass(EntryRequestedEvent.class);
        verify(entryRequestProducer).send(captor.capture());
        assertThat(captor.getValue().requestId()).isEqualTo(event.requestId());
        assertThat(captor.getValue().redriveCount()).isEqualTo(1);
        verify(entryTokenManager, never()).markFailed(any());
    }

    // 재투입까지 실패한 요청은 다시 보내지 않고 줄에서 빼고 실패로 표시해야 한다 (무한 재처리 방지)
    @Test
    @DisplayName("재투입 횟수를 다 쓴 DLT 요청은 줄에서 빼고 최종 실패로 표시한다")
    void handleDeadLetter_exhausted_marksFailed() {
        // given
        EntryRequestedEvent event = EntryRequestedEvent.of(1L).redriven();

        // when
        entryService.handleDeadLetter(event);

        // then
        verify(entryRequestProducer, never()).send(any(EntryRequestedEvent.class));
        verify(waitingLineManager).leave(1L, event.requestId());
        verify(entryTokenManager).markFailed(event.requestId());
    }

    // 줄에도 없고 토큰도 없는 요청은 순번 없이 WAITING이어야 한다
    @Test
    @DisplayName("줄에서 찾을 수 없는 요청은 순번 없이 WAITING을 반환한다")
    void getEntryStatus_unknownPosition() {
        // given
        when(entryTokenManager.findToken("unknown-id")).thenReturn(Optional.empty());
        when(waitingLineManager.findPosition(1L, "unknown-id")).thenReturn(Optional.empty());

        // when
        EntryStatusResponse waiting = entryService.getEntryStatus(1L, "unknown-id");

        // then
        assertThat(waiting.status()).isEqualTo(EntryStatus.WAITING);
        assertThat(waiting.position()).isNull();
        assertThat(waiting.estimatedSeconds()).isNull();
    }
}
