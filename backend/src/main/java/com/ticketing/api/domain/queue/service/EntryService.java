package com.ticketing.api.domain.queue.service;

import com.ticketing.api.domain.queue.dto.EntryRequestResponse;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.dto.EntryStatusResponse;

/**
 * 입장 요청 접수, 대기열 소비 시의 입장 허용(토큰 발급), 상태 조회 비즈니스 로직 인터페이스.
 */
public interface EntryService {

    /**
     * 입장 요청을 접수해서 대기 순번을 매기고 대기열(entry.requested)에 적재한다.
     * 발행 결과를 기다리지 않고 응답하며, 발행이 실패하면 줄에서 빼고 최종 실패(FAILED)로 표시한다.
     *
     * @param eventId 입장할 이벤트 ID (없으면 EVENT_NOT_FOUND 예외)
     * @return 접수된 요청 ID
     */
    EntryRequestResponse requestEntry(Long eventId);

    /**
     * 대기열에서 꺼낸 입장 요청을 허용한다. 입장 토큰을 발급하고 entry.granted를 발행하며,
     * 같은 요청이 중복 전달되면 아무것도 하지 않는다(멱등).
     *
     * @param event 소비한 입장 요청 이벤트
     */
    void grantEntry(EntryRequestedEvent event);

    /**
     * 재시도 끝에 DLT로 빠진 입장 요청을 처리한다. 재투입 횟수가 남아 있으면 원래 토픽으로 다시 보내고,
     * 남아 있지 않거나 재투입 발행이 실패하면 대기 줄에서 빼고 최종 실패로 표시한다.
     *
     * @param event DLT에서 꺼낸 입장 요청 이벤트
     */
    void handleDeadLetter(EntryRequestedEvent event);

    /**
     * 대기열을 거치지 않고 즉시 입장 토큰을 발급한다. 대기열 적용 전/후 부하 비교용 기준선(baseline) 시나리오에서만 쓴다.
     *
     * @param eventId 입장할 이벤트 ID (없으면 EVENT_NOT_FOUND 예외)
     * @return 즉시 허용(GRANTED)된 상태와 토큰
     */
    EntryStatusResponse grantEntryDirectly(Long eventId);

    /**
     * 입장 요청의 처리 상태를 조회한다.
     *
     * @param eventId   이벤트 ID (이벤트별 대기열에서 순번을 찾는다)
     * @param requestId 입장 요청 ID
     * @return 대기 중이면 순번/예상 대기 시간을 포함한 WAITING, 허용되었으면 토큰을 포함한 GRANTED,
     *         재처리까지 실패했으면 FAILED
     */
    EntryStatusResponse getEntryStatus(Long eventId, String requestId);
}
