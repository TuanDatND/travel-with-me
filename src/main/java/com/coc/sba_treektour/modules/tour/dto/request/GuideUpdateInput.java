package com.coc.sba_treektour.modules.tour.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GuideUpdateInput(
        @NotNull @PositiveOrZero Integer experienceYears,
        @NotBlank @Size(max = 1000) String specialization,
        @Size(max = 5000) String bio,
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> languages) {}
