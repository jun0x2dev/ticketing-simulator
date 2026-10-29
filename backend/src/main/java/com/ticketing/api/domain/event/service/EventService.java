package com.ticketing.api.domain.event.service;

import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;
import java.time.LocalDateTime;

/**
 * 이벤트(공연/행사) 생성 및 조회 비즈니스 로직 인터페이스.
 */
public interface EventService {

    /**
     * 새 이벤트를 생성한다.
     *
     * @param request 생성 요청 정보
     * @return 생성된 이벤트 응답
     */
    EventResponse createEvent(EventCreateRequest request);

    /**
     * 이벤트를 단건 조회한다.
     *
     * @param eventId 조회할 이벤트 ID
     * @return 이벤트 응답
     */
    EventResponse getEvent(Long eventId);

    /**
     * 오픈 시각이 기준 시각보다 이전인 회차를 최대 batchSize개 골라 좌석과 함께 삭제한다.
     *
     * @param openedBefore 이 시각 이전에 열린 회차가 대상
     * @param batchSize    한 번에 삭제할 최대 회차 수
     * @return 삭제한 회차 수 (batchSize와 같으면 남은 대상이 더 있을 수 있다)
     */
    int deleteRunsOpenedBefore(LocalDateTime openedBefore, int batchSize);
}
