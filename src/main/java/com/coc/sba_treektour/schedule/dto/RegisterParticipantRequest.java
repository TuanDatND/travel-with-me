package com.coc.sba_treektour.schedule.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RegisterParticipantRequest(
        @NotNull(message = "userId is required")
        Long userId,

        @NotNull(message = "registeredPrice is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "registeredPrice cannot be negative")
        BigDecimal registeredPrice,

        String note
) {}
