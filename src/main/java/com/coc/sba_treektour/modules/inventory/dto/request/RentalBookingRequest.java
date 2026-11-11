package com.coc.sba_treektour.modules.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record RentalBookingRequest(@NotNull @Positive Long branchId, @NotNull @Positive Long productId,
        @Positive int quantity, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
