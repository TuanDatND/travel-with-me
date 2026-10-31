package com.coc.sba_treektour.modules.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AdjustRequest(@NotNull @PositiveOrZero Integer quantityTotal,
        @NotNull @PositiveOrZero Integer quantityAvailable) {}
