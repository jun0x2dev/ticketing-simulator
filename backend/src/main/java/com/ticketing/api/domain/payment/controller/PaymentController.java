package com.ticketing.api.domain.payment.controller;

import com.ticketing.api.domain.payment.dto.PaymentRequest;
import com.ticketing.api.domain.payment.dto.PaymentResponse;
import com.ticketing.api.domain.payment.service.PaymentService;
import com.ticketing.api.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mock 결제 요청을 처리하는 컨트롤러.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 선점된 좌석에 대해 Mock 결제를 수행한다.
     *
     * @param request 결제할 좌석 ID
     * @return 결제 결과 (승인: SUCCESS, 거절: FAILED)
     */
    @PostMapping
    public ApiResponse<PaymentResponse> pay(@Valid @RequestBody PaymentRequest request) {
        return ApiResponse.success(paymentService.pay(request.seatId()));
    }
}
