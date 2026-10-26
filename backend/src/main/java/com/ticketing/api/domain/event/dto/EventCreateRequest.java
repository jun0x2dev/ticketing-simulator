package com.ticketing.api.domain.event.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 이벤트 생성 요청 DTO.
 * openAt은 항상 미래 시각이어야 한다(이미 지난 오픈 시각으로 생성하는 것을 방지).
 */
public record EventCreateRequest(
        @NotBlank String name,
        @Min(1) int seatCount,
        @NotNull @Future LocalDateTime openAt
) {
}
