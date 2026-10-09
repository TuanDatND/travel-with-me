package com.coc.sba_treektour.schedule.dto;

import com.coc.sba_treektour.schedule.entity.ScheduleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateScheduleStatusRequest(
        @NotNull(message = "status is required")
        ScheduleStatus status,

        String changeReason
) {}
