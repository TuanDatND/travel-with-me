package com.coc.sba_treektour.modules.inventory.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class InventoryDtos {
    private InventoryDtos() {}
    public record ImportRequest(@Positive int quantity) {}
    public record RentalBookingRequest(@NotNull @Positive Long branchId, @NotNull @Positive Long productId,
            @Positive int quantity, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
    // Không cho ghi đè rented: số đang thuê chỉ đổi qua service giao/trả.
    public record AdjustRequest(@NotNull @PositiveOrZero Integer quantityTotal, @NotNull @PositiveOrZero Integer quantityAvailable) {}
    public record InventoryResponse(Long id, Long branchId, Long productId, String productName,
            int quantityTotal, int quantityAvailable, int quantityRented, String status) {}
    public record AvailabilityResponse(Long branchId, Long productId, LocalDate startDate,
            LocalDate endDate, int quantityAvailable, long peakReserved, int quantityRentable) {}
    public record SaleAvailabilityResponse(Long branchId, Long productId, int quantityAvailable) {}
}
