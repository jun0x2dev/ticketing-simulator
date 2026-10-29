package com.ticketing.api.domain.stats.controller;

import com.ticketing.api.domain.stats.dto.EventStatsResponse;
import com.ticketing.api.domain.stats.service.EventStatsService;
import com.ticketing.api.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회차별 예매 통계 조회 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
public class EventStatsController {

    private final EventStatsService eventStatsService;

    /**
     * 회차의 입장 허용/결제 완료/결제 실패/시간 초과 해제 수를 조회한다.
     *
     * @param eventId 회차(이벤트) ID
     * @return 회차 통계
     */
    @GetMapping("/api/v1/events/{eventId}/stats")
    public ApiResponse<EventStatsResponse> getStats(@PathVariable Long eventId) {
        return ApiResponse.success(eventStatsService.getStats(eventId));
    }
}
