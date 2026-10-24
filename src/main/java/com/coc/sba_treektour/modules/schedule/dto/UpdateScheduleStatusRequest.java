package com.coc.sba_treektour.modules.schedule.dto;

import com.coc.sba_treektour.modules.schedule.entity.ScheduleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateScheduleStatusRequest(
        @NotNull(message = "status is required")
        ScheduleStatus status,

        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 2000) String changeReason
) {}
