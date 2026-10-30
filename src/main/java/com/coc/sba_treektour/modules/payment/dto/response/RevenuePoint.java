package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenuePoint(LocalDate date, BigDecimal receivedAmount, BigDecimal refundedAmount, BigDecimal netAmount) {}
