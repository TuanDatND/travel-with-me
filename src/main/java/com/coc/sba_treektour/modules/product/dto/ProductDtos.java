package com.coc.sba_treektour.modules.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class ProductDtos {
    private ProductDtos() {}
    public record ProductRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String category,
            @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal salePrice,
            @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal rentalPricePerDay) {}
    public record StatusRequest(@NotNull Status status) {}
    public enum Status { ACTIVE, INACTIVE }
    public record ProductResponse(Long id, String name, String category, BigDecimal salePrice,
            BigDecimal rentalPricePerDay, String status, OffsetDateTime createdAt) {}
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
}
