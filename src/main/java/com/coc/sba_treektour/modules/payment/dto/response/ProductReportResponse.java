package com.coc.sba_treektour.modules.payment.dto.response;

import java.util.List;

public record ProductReportResponse(String metric, String timeBasis, List<ProductRevenueRow> products, List<ProductRevenuePoint> series) {}
