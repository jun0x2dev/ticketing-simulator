package com.ticketing.api.domain.queue.controller;

import com.ticketing.api.domain.queue.dto.EntryStatusResponse;
import com.ticketing.api.domain.queue.service.EntryService;
import com.ticketing.api.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열을 거치지 않는 즉시 입장 API. "대기열 적용 전" 부하 시나리오(k6 direct.js)의 기준선 전용이다.
 * 누구나 대기열을 건너뛸 수 있는 구멍이므로 로컬/부하 측정(bench) 프로필에서만 등록한다.
 */
@RestController
@Profile({"local", "bench"})
@RequiredArgsConstructor
public class DirectEntryController {

    private final EntryService entryService;

    /**
     * 대기열 없이 즉시 입장 토큰을 발급한다.
     *
     * @param eventId 입장할 이벤트 ID
     * @return 즉시 허용된 상태와 토큰
     */
    @PostMapping("/api/v1/events/{eventId}/entries/direct")
    public ApiResponse<EntryStatusResponse> requestEntryDirectly(@PathVariable Long eventId) {
        return ApiResponse.success(entryService.grantEntryDirectly(eventId));
    }
}
