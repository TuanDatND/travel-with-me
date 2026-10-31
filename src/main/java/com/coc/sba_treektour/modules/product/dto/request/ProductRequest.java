package com.coc.sba_treektour.modules.product.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String category,
            @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal salePrice,
        @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal rentalPricePerDay) {}
