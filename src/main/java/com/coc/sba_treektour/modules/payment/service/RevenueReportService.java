package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.dto.request.RevenueReportRequest;
import jakarta.validation.Valid;
import com.coc.sba_treektour.modules.payment.dto.response.ProductReportResponse;
import com.coc.sba_treektour.modules.payment.dto.response.ReportTransactionPage;
import com.coc.sba_treektour.modules.payment.dto.response.RevenueOverviewResponse;
import com.coc.sba_treektour.modules.payment.dto.response.TourReportResponse;

public interface RevenueReportService {
    RevenueOverviewResponse overview(@Valid RevenueReportRequest request);
    ProductReportResponse products(@Valid RevenueReportRequest request);
    TourReportResponse tours(@Valid RevenueReportRequest request);
    ReportTransactionPage transactions(@Valid RevenueReportRequest request, int page, int size);
    String csv(@Valid RevenueReportRequest request);
}
