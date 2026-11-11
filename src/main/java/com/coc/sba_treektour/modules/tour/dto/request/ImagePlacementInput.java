package com.coc.sba_treektour.modules.tour.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ImagePlacementInput(
        @NotNull @Positive Long imageId,
        @Positive @Max(30) Integer dayNumber) {}
