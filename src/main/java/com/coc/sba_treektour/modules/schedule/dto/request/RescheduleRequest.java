package com.coc.sba_treektour.modules.schedule.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record RescheduleRequest(
        @NotNull(message = "newStartDatetime is required")
        OffsetDateTime newStartDatetime,

        @NotNull(message = "newEndDatetime is required")
        OffsetDateTime newEndDatetime,

        @NotBlank(message = "changeReason must not be blank")
        @Size(max = 2000) String changeReason,

        OffsetDateTime registrationClosesAt
) {}
