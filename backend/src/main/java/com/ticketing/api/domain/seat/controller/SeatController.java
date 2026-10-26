package com.ticketing.api.domain.seat.controller;

import com.ticketing.api.domain.seat.dto.SeatResponse;
import com.ticketing.api.domain.seat.service.SeatService;
import com.ticketing.api.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 좌석 조회 및 선점/해제를 처리하는 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    /**
     * 특정 이벤트에 속한 좌석 전체를 조회한다.
     *
     * @param eventId 이벤트 ID
     * @return 좌석 목록
     */
    @GetMapping("/api/v1/events/{eventId}/seats")
    public ApiResponse<List<SeatResponse>> getSeats(@PathVariable Long eventId) {
        return ApiResponse.success(seatService.getSeats(eventId));
    }

    /**
     * 좌석을 선점한다. 입장 토큰이 필요하며, 동시 요청이 들어와도 Redis 락 덕분에 단 하나만 성공한다.
     *
     * @param seatId     선점할 좌석 ID
     * @param entryToken 입장 허용 후 발급받은 토큰 (X-Entry-Token 헤더)
     * @return 선점된 좌석 정보
     */
    @PostMapping("/api/v1/seats/{seatId}/hold")
    public ApiResponse<SeatResponse> holdSeat(
            @PathVariable Long seatId,
            @RequestHeader(value = "X-Entry-Token", required = false) String entryToken) {
        return ApiResponse.success(seatService.holdSeat(seatId, entryToken));
    }

    /**
     * 선점된 좌석을 다시 선택 가능 상태로 되돌린다.
     *
     * @param seatId 해제할 좌석 ID
     */
    @PostMapping("/api/v1/seats/{seatId}/release")
    public ApiResponse<Void> releaseSeat(@PathVariable Long seatId) {
        seatService.releaseSeat(seatId);
        return ApiResponse.success(null);
    }
}
