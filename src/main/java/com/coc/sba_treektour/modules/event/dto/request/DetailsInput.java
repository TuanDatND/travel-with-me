package com.coc.sba_treektour.modules.event.dto.request;

import com.coc.sba_treektour.modules.event.enums.DifficultyLevel;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DetailsInput(
        @Positive Integer durationMinutes,
        DifficultyLevel difficultyLevel,
        @Size(max = 10000) String meetingPoint,
        @Size(max = 10000) String requirements) {}
