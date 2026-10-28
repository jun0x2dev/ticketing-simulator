package com.ticketing.api.domain.queue.service;

import com.ticketing.api.domain.queue.dto.EntryRequestResponse;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.dto.EntryStatusResponse;

/**
 * 입장 요청 접수, 대기열 소비 시의 입장 허용(토큰 발급), 상태 조회 비즈니스 로직 인터페이스.
 */
public interface EntryService {

    /**
     * 입장 요청을 접수해서 대기열(entry.requested)에 적재한다.
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
     * 대기열을 거치지 않고 즉시 입장 토큰을 발급한다. 대기열 적용 전/후 부하 비교용 기준선(baseline) 시나리오에서만 쓴다.
     *
     * @param eventId 입장할 이벤트 ID (없으면 EVENT_NOT_FOUND 예외)
     * @return 즉시 허용(GRANTED)된 상태와 토큰
     */
    EntryStatusResponse grantEntryDirectly(Long eventId);

    /**
     * 입장 요청의 처리 상태를 조회한다.
     *
     * @param requestId 입장 요청 ID
     * @return 대기 중이면 WAITING, 허용되었으면 토큰을 포함한 GRANTED
     */
    EntryStatusResponse getEntryStatus(String requestId);
}
