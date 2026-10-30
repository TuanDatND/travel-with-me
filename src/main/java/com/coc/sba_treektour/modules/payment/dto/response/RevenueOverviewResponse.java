package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RevenueOverviewResponse(LocalDate from, LocalDate to, String timezone, String timeBasis, MoneyTotals totals, BigDecimal pendingRefundAmount, long pendingRefundCount, long undatedSucceededCount, BigDecimal undatedReceivedAmount, BigDecimal undatedRefundedAmount, List<RevenuePoint> series, List<RevenueBreakdown> branches, List<RevenueBreakdown> sectors) {}
