package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ScheduleRevenueRow(Long scheduleId, Long tourId, String tourName, OffsetDateTime startDatetime, Long branchId, String branchName, long paidTicketCount, BigDecimal receivedAmount, BigDecimal refundedAmount, BigDecimal netAmount) {}
