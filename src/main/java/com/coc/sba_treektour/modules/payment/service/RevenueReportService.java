package com.coc.sba_treektour.modules.payment.service;

import com.coc.sba_treektour.modules.payment.dto.request.RevenueReportRequest;
import com.coc.sba_treektour.modules.payment.dto.response.ProductReportResponse;
import com.coc.sba_treektour.modules.payment.dto.response.ReportTransactionPage;
import com.coc.sba_treektour.modules.payment.dto.response.RevenueOverviewResponse;
import com.coc.sba_treektour.modules.payment.dto.response.TourReportResponse;

public interface RevenueReportService {
    RevenueOverviewResponse overview(RevenueReportRequest request);
    ProductReportResponse products(RevenueReportRequest request);
    TourReportResponse tours(RevenueReportRequest request);
    ReportTransactionPage transactions(RevenueReportRequest request, int page, int size);
    String csv(RevenueReportRequest request);
}
