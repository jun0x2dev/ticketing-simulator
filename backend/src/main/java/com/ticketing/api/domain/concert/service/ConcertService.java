package com.ticketing.api.domain.concert.service;

import com.ticketing.api.domain.concert.dto.ConcertResponse;
import com.ticketing.api.domain.event.dto.EventResponse;
import java.util.List;

/**
 * 공연 목록 조회와 공연별 새 회차(Event) 생성 비즈니스 로직 인터페이스.
 */
public interface ConcertService {

    /**
     * 기본 공연 목록이 비어 있으면 시드 데이터를 넣는다. 이미 있으면 아무것도 하지 않는다.
     */
    void initializeCatalog();

    /**
     * 예매 가능한 공연 목록을 조회한다.
     *
     * @return 공연 목록
     */
    List<ConcertResponse> getConcerts();

    /**
     * 공연 템플릿으로 새 회차를 만든다. 좌석 수는 템플릿 값을 그대로 쓰므로 요청으로 바꿀 수 없다.
     *
     * @param concertId 공연 ID (없으면 CONCERT_NOT_FOUND 예외)
     * @return 생성된 회차(이벤트) 정보
     */
    EventResponse startRun(Long concertId);
}
