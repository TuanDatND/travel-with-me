package com.coc.sba_treektour.modules.payment.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.payment.dto.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.modules.payment.dto.ZaloPayPaymentResponse;
import com.coc.sba_treektour.modules.payment.service.TransactionsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/zalopay")
@RequiredArgsConstructor
public class ZaloPayController {
    private final TransactionsService transactionsService;

    @PostMapping("/")
    public ApiResponse<ZaloPayPaymentResponse> createPayment(
            @Valid @RequestBody CreateZaloPayPaymentRequest request) {
        return ApiResponse.created(
                "Tạo giao dịch ZaloPay thành công",
                transactionsService.createZaloPayPayment(request));
    }

    @GetMapping("/query/{transactionId}")
    public ApiResponse<ZaloPayPaymentResponse> queryPayment(@PathVariable Long transactionId) {
        return ApiResponse.success(
                "Truy vấn giao dịch ZaloPay thành công",
                transactionsService.queryZaloPayPayment(transactionId));
    }

}
