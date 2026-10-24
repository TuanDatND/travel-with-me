package com.coc.sba_treektour.modules.event.dto.request;

import com.coc.sba_treektour.modules.event.enums.EventType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EventInput(
        @NotBlank @Size(max = 200) String eventName,
        @NotNull EventType eventType,
        @Size(max = 50000) String description,
        @Size(max = 10000) String location,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal basePrice,
        @Valid DetailsInput details) {}
