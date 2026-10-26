package com.ticketing.api.domain.event.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record EventCreateRequest(
        @NotBlank String name,
        @Min(1) int seatCount,
        @NotNull @Future LocalDateTime openAt
) {
}
