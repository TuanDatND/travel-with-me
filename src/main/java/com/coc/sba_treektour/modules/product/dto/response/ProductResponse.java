package com.coc.sba_treektour.modules.product.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(Long id, String name, String category, BigDecimal salePrice,
        BigDecimal rentalPricePerDay, String status, OffsetDateTime createdAt) {}
