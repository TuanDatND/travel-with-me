package com.coc.sba_treektour.modules.payment.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.payment.dto.request.RevenueReportRequest;
import com.coc.sba_treektour.modules.payment.dto.response.ProductReportResponse;
import com.coc.sba_treektour.modules.payment.dto.response.ReportTransactionPage;
import com.coc.sba_treektour.modules.payment.dto.response.RevenueOverviewResponse;
import com.coc.sba_treektour.modules.payment.dto.response.TourReportResponse;
import com.coc.sba_treektour.modules.payment.service.RevenueReportService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reports/revenue")
public class RevenueReportController {
    private final RevenueReportService reports;

    @GetMapping
    public ApiResponse<RevenueOverviewResponse> overview(@Valid @ModelAttribute RevenueReportRequest request) {
        return ApiResponse.success(reports.overview(request));
    }
    @GetMapping("/products")
    public ApiResponse<ProductReportResponse> products(@Valid @ModelAttribute RevenueReportRequest request) {
        return ApiResponse.success(reports.products(request));
    }
    @GetMapping("/tours")
    public ApiResponse<TourReportResponse> tours(@Valid @ModelAttribute RevenueReportRequest request) {
        return ApiResponse.success(reports.tours(request));
    }
    @GetMapping("/transactions")
    public ApiResponse<ReportTransactionPage> transactions(@Valid @ModelAttribute RevenueReportRequest request,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(reports.transactions(request, page, size));
    }
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@Valid @ModelAttribute RevenueReportRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(reports.csv(request).getBytes(StandardCharsets.UTF_8));
    }
}
