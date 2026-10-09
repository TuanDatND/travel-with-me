package com.coc.sba_treektour.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record RescheduleRequest(
        @NotNull(message = "newStartDatetime is required")
        OffsetDateTime newStartDatetime,

        @NotNull(message = "newEndDatetime is required")
        OffsetDateTime newEndDatetime,

        @NotBlank(message = "changeReason must not be blank")
        String changeReason
) {}
