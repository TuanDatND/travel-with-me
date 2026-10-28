package com.coc.sba_treektour.modules.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PackingItemInput(
        @NotBlank @Size(max = 1000) String text, @NotNull Boolean required) {}
