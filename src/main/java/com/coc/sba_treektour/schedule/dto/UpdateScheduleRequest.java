package com.coc.sba_treektour.schedule.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateScheduleRequest(
        Long guideId,

        @NotNull(message = "maxParticipants is required")
        @Min(value = 1, message = "maxParticipants must be at least 1")
        Integer maxParticipants
) {}
