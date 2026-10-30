package com.coc.sba_treektour.modules.payment.dto.response;

import java.util.List;

public record TourReportResponse(List<TourRevenueRow> tours, List<TourRevenuePoint> series, List<ScheduleRevenueRow> schedules) {}
