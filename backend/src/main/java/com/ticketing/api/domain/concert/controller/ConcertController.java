package com.ticketing.api.domain.concert.controller;

import com.ticketing.api.domain.concert.dto.ConcertResponse;
import com.ticketing.api.domain.concert.service.ConcertService;
import com.ticketing.api.domain.event.dto.EventResponse;
import com.ticketing.api.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공연 목록 조회와 공연별 예매 회차 시작을 처리하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    /**
     * 예매 가능한 공연 목록을 조회한다.
     *
     * @return 공연 목록
     */
    @GetMapping
    public ApiResponse<List<ConcertResponse>> getConcerts() {
        return ApiResponse.success(concertService.getConcerts());
    }

    /**
     * 공연의 새 회차를 만든다. 사용자가 공연을 고를 때마다 빈 좌석에서 시작하는 회차가 생긴다.
     *
     * @param concertId 공연 ID
     * @return 생성된 회차(이벤트) 정보
     */
    @PostMapping("/{concertId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> startRun(@PathVariable Long concertId) {
        return ApiResponse.success(concertService.startRun(concertId));
    }
}
