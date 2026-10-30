package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;

public record TourRevenueRow(Long tourId, String tourName, long paidTicketCount, BigDecimal receivedAmount, BigDecimal refundedAmount, BigDecimal netAmount) {}
