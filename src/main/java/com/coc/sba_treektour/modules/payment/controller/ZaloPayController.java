package com.coc.sba_treektour.modules.payment.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.payment.dto.CreateZaloPayPaymentRequest;
import com.coc.sba_treektour.modules.payment.dto.ZaloPayPaymentResponse;
import com.coc.sba_treektour.modules.payment.service.TransactionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @PostMapping
    @Operation(description = "Chọn một loại thanh toán: orderId cho đơn thiết bị hoặc participantId cho vé tour. Giá, người trả và return URL do backend xác định.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateZaloPayPaymentRequest.class),
                            examples = {
                                    @ExampleObject(name = "Đơn thiết bị", value = "{\"orderId\":5}"),
                                    @ExampleObject(name = "Vé tour", value = "{\"participantId\":7}")
                            })))
    public ApiResponse<ZaloPayPaymentResponse> createPayment(
            @Valid @RequestBody CreateZaloPayPaymentRequest request) {
        return ApiResponse.created(
                "Tạo giao dịch ZaloPay thành công",
                transactionsService.createZaloPayPayment(request));
    }

    @PostMapping("/retry")
    @Operation(description = "Chọn một loại thanh toán: orderId cho đơn thiết bị hoặc participantId cho vé tour. Giá, người trả và return URL do backend xác định.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateZaloPayPaymentRequest.class),
                            examples = {
                                    @ExampleObject(name = "Đơn thiết bị", value = "{\"orderId\":5}"),
                                    @ExampleObject(name = "Vé tour", value = "{\"participantId\":7}")
                            })))
    public ApiResponse<ZaloPayPaymentResponse> retryOrderPayment(
            @Valid @RequestBody CreateZaloPayPaymentRequest request) {
        return ApiResponse.success("Đã xử lý thanh toán lại", transactionsService.retryOrderPayment(request));
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<ZaloPayPaymentResponse> queryOrderPayment(@PathVariable Long orderId) {
        return ApiResponse.success("Đã kiểm tra thanh toán của đơn", transactionsService.queryOrderPayment(orderId));
    }

    @GetMapping("/participants/{participantId}")
    public ApiResponse<ZaloPayPaymentResponse> queryParticipantPayment(@PathVariable Long participantId) {
        return ApiResponse.success("Đã kiểm tra thanh toán vé", transactionsService.queryParticipantPayment(participantId));
    }

    @PostMapping("/{transactionId}/cancel")
    public ApiResponse<ZaloPayPaymentResponse> cancelPayment(@PathVariable Long transactionId) {
        return ApiResponse.success("Đã kiểm tra yêu cầu hủy thanh toán",
                transactionsService.cancelZaloPayPayment(transactionId));
    }

    @GetMapping("/query/{transactionId}")
    public ApiResponse<ZaloPayPaymentResponse> queryPayment(@PathVariable Long transactionId) {
        return ApiResponse.success(
                "Truy vấn giao dịch ZaloPay thành công",
                transactionsService.queryZaloPayPayment(transactionId));
    }

}
