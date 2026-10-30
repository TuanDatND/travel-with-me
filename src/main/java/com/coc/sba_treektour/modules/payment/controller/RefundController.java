package com.coc.sba_treektour.modules.payment.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.payment.dto.request.RequestRefund;
import com.coc.sba_treektour.modules.payment.dto.response.TransactionHistoryResponse;
import com.coc.sba_treektour.modules.payment.service.RefundService;
import jakarta.validation.Valid;
import com.coc.sba_treektour.modules.payment.enums.TransactionStatus;
import com.coc.sba_treektour.modules.payment.dto.response.RefundPreviewResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/refunds")
public class RefundController {
    private final RefundService refunds;

    @GetMapping("/preview")
    public ApiResponse<RefundPreviewResponse> preview(@RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long participantId) {
        return ApiResponse.success(refunds.preview(orderId, participantId));
    }

    @PostMapping
    public ApiResponse<TransactionHistoryResponse> request(@Valid @RequestBody RequestRefund request) {
        return ApiResponse.success("Refund requested; money has not been returned yet", refunds.request(request));
    }

    @GetMapping("/me")
    public ApiResponse<List<TransactionHistoryResponse>> mine(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(refunds.mine(page, size));
    }

    @GetMapping
    public ApiResponse<List<TransactionHistoryResponse>> list(@RequestParam(defaultValue = "PENDING") TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(refunds.list(status, page, size));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<TransactionHistoryResponse> confirm(@PathVariable Long id) {
        return ApiResponse.success("Manual refund recorded; no ZaloPay request was made", refunds.confirm(id));
    }
}
