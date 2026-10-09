package com.coc.sba_treektour.payment.controller;

import com.coc.sba_treektour.payment.dto.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.payment.dto.ZaloPayCallbackRequest;
import com.coc.sba_treektour.payment.dto.ZaloPayCallbackResponse;
import com.coc.sba_treektour.payment.dto.ZaloPayPaymentResponse;
import com.coc.sba_treektour.payment.service.TransactionsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/create")
    public ResponseEntity<ZaloPayPaymentResponse> createPayment(
            @Valid @RequestBody CreateZaloPayPaymentRequest request) {
        return ResponseEntity.ok(transactionsService.createZaloPayPayment(request));
    }

    @GetMapping("/query/{transactionId}")
    public ResponseEntity<ZaloPayPaymentResponse> queryPayment(@PathVariable Long transactionId) {
        return ResponseEntity.ok(transactionsService.queryZaloPayPayment(transactionId));
    }

    @PostMapping("/callback")
    public ResponseEntity<ZaloPayCallbackResponse> callback(
            @Valid @RequestBody ZaloPayCallbackRequest request) {
        return ResponseEntity.ok(transactionsService.processZaloPayCallback(request));
    }
}
