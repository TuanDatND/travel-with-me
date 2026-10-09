package com.coc.sba_treektour.payment.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.payment.dto.TransactionHistoryResponse;
import com.coc.sba_treektour.payment.service.TransactionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/payments/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionsService transactionsService;

    @GetMapping("/history")
    public ApiResponse<List<TransactionHistoryResponse>> getTransactionHistory() {
        return ApiResponse.success(
                "Lấy lịch sử giao dịch thành công",
                transactionsService.getTransactionHistory());
    }
}
