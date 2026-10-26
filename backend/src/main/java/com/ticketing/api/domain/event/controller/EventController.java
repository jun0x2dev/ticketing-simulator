package com.ticketing.api.domain.event.controller;

import com.ticketing.api.domain.event.dto.EventCreateRequest;
import com.ticketing.api.domain.event.dto.EventResponse;
import com.ticketing.api.domain.event.service.EventService;
import com.ticketing.api.domain.queue.dto.EntryRequestedEvent;
import com.ticketing.api.domain.queue.producer.EntryRequestProducer;
import com.ticketing.api.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final EntryRequestProducer entryRequestProducer;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> createEvent(@Valid @RequestBody EventCreateRequest request) {
        return ApiResponse.success(eventService.createEvent(request));
    }

    @GetMapping("/{eventId}")
    public ApiResponse<EventResponse> getEvent(@PathVariable Long eventId) {
        return ApiResponse.success(eventService.getEvent(eventId));
    }

    /**
     * 가상 유저 1명의 입장 요청을 발행하는 수동 테스트용 엔드포인트.
     * load-generator 구현 전까지 Kafka 연동 확인용으로 사용한다.
     */
    @PostMapping("/{eventId}/entry-requests")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> requestEntry(@PathVariable Long eventId) {
        eventService.getEvent(eventId);
        entryRequestProducer.send(EntryRequestedEvent.of(eventId));
        return ApiResponse.success(null);
    }
}
