package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TourRevenuePoint(LocalDate date, Long tourId, String tourName, BigDecimal receivedAmount, BigDecimal refundedAmount, BigDecimal netAmount) {}
