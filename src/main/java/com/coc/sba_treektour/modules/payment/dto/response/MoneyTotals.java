package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;

public record MoneyTotals(BigDecimal receivedAmount, BigDecimal refundedAmount, BigDecimal netAmount, long paymentCount, long refundCount) {}
