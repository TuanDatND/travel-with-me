package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProductRevenuePoint(LocalDate date, Long productId, String productName, BigDecimal saleValue, BigDecimal rentalValue, BigDecimal totalValue) {}
