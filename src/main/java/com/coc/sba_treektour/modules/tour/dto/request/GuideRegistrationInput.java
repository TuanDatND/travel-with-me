package com.coc.sba_treektour.modules.tour.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GuideRegistrationInput(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        @NotNull @PositiveOrZero Integer experienceYears,
        @NotBlank @Size(max = 1000) String specialization,
        @Size(max = 5000) String bio,
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> languages) {}
