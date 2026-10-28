package com.coc.sba_treektour.modules.schedule.dto.request;

import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateScheduleStatusRequest(
        @NotNull(message = "status is required")
        ScheduleStatus status,

        @NotBlank
        @Size(max = 2000) String changeReason
) {}
