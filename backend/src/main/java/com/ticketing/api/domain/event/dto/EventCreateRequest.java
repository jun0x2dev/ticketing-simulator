package com.ticketing.api.domain.event.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 이벤트 생성 요청 DTO.
 * openAt은 항상 미래 시각이어야 한다(이미 지난 오픈 시각으로 생성하는 것을 방지).
 * 좌석 수는 로컬 PC에서 감당할 수 있는 규모로 상한을 둔다(좌석 행 생성과 전체 좌석 조회 부하 제한).
 */
public record EventCreateRequest(
        @NotBlank String name,
        @Min(1) @Max(EventCreateRequest.MAX_SEAT_COUNT) int seatCount,
        @NotNull @Future LocalDateTime openAt
) {

    public static final int MAX_SEAT_COUNT = 500;
}
