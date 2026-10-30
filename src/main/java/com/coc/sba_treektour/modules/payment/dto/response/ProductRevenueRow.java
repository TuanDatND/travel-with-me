package com.coc.sba_treektour.modules.payment.dto.response;

import java.math.BigDecimal;

public record ProductRevenueRow(Long productId, String productName, long saleQuantity, long rentalQuantity, long rentalDays, long paidOrderCount, BigDecimal saleValue, BigDecimal rentalValue, BigDecimal totalValue) {}
