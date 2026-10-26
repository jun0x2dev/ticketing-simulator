package com.ticketing.api.domain.event.controller;

import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;
import com.ticketing.api.domain.event.service.EventService;
import com.ticketing.api.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이벤트(공연/행사) 생성 및 조회를 처리하는 컨트롤러.
 * 입장 요청(Kafka 발행)은 simulation 도메인의 load-generator가 담당하며,
 * 여기서는 이벤트 CRUD만 다룬다.
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * 새 이벤트를 생성한다.
     *
     * @param request 이벤트명, 좌석 수, 오픈 시각
     * @return 생성된 이벤트 정보
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> createEvent(@Valid @RequestBody EventCreateRequest request) {
        return ApiResponse.success(eventService.createEvent(request));
    }

    /**
     * 이벤트 ID로 단건 조회한다.
     *
     * @param eventId 조회할 이벤트 ID
     * @return 이벤트 정보 (없으면 EVENT_NOT_FOUND 예외)
     */
    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> getEvent(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.getEvent(eventId));
    }
}
