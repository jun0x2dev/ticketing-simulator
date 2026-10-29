package com.ticketing.api.domain.queue.controller;

import com.ticketing.api.domain.queue.dto.EntryRequestResponse;
import com.ticketing.api.domain.queue.dto.EntryStatusResponse;
import com.ticketing.api.domain.queue.service.EntryService;
import com.ticketing.api.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입장 요청 접수와 입장 허용 상태 조회를 처리하는 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
public class EntryController {

    private final EntryService entryService;

    /**
     * 입장 요청을 대기열에 적재한다. 처리는 비동기이므로 접수만 하고 202로 응답한다.
     *
     * @param eventId 입장할 이벤트 ID
     * @return 접수된 요청 ID
     */
    @PostMapping("/api/v1/events/{eventId}/entries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<EntryRequestResponse> requestEntry(@PathVariable Long eventId) {
        return ApiResponse.success(entryService.requestEntry(eventId));
    }

    /**
     * 대기열 없이 즉시 입장 토큰을 발급한다. "대기열 적용 전" 부하 시나리오(k6)를 재현하기 위한 시뮬레이션용 API다.
     *
     * @param eventId 입장할 이벤트 ID
     * @return 즉시 허용된 상태와 토큰
     */
    @PostMapping("/api/v1/events/{eventId}/entries/direct")
    public ApiResponse<EntryStatusResponse> requestEntryDirectly(@PathVariable Long eventId) {
        return ApiResponse.success(entryService.grantEntryDirectly(eventId));
    }

    /**
     * 입장 요청의 처리 상태를 조회한다. 대기 중이면 대기 순번과 예상 대기 시간이,
     * 허용되었으면 좌석 선점에 쓸 입장 토큰이 함께 반환된다.
     *
     * @param eventId   이벤트 ID
     * @param requestId 입장 요청 ID
     * @return 대기/허용 상태
     */
    @GetMapping("/api/v1/events/{eventId}/entries/{requestId}")
    public ApiResponse<EntryStatusResponse> getEntryStatus(@PathVariable Long eventId, @PathVariable String requestId) {
        return ApiResponse.success(entryService.getEntryStatus(eventId, requestId));
    }
}
