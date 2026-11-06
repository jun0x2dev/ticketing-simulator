package com.ticketing.api.domain.payment.controller;

import com.ticketing.api.domain.payment.dto.PaymentRequest;
import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.service.PaymentService;
import com.ticketing.api.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock 결제와 예매 취소 요청을 처리하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 선점된 좌석에 대해 Mock 결제를 수행한다.
     *
     * @param request    결제할 좌석 ID
     * @param entryToken 좌석을 선점할 때 쓴 입장 토큰 (X-Entry-Token 헤더)
     * @return 결제 결과 (승인: SUCCESS, 거절: FAILED)
     */
    @PostMapping
    public ApiResponse<PaymentResponse> pay(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "X-Entry-Token", required = false) String entryToken) {
        return ApiResponse.success(paymentService.pay(request.seatId(), entryToken));
    }

    /**
     * 예매를 취소하고 환불한다. 좌석은 다시 선택 가능 상태로 열린다.
     *
     * @param paymentId  취소할 결제 ID
     * @param entryToken 결제할 때 쓴 입장 토큰 (X-Entry-Token 헤더)
     * @return 취소된 결제 (REFUNDED)
     */
    @PostMapping("/{paymentId}/cancel")
    public ApiResponse<PaymentResponse> cancel(
            @PathVariable Long paymentId,
            @RequestHeader(value = "X-Entry-Token", required = false) String entryToken) {
        return ApiResponse.success(paymentService.cancel(paymentId, entryToken));
    }
}
