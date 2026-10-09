package com.coc.sba_treektour.schedule.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record CreateScheduleRequest(
        @NotNull(message = "eventId is required")
        Long eventId,

        @NotNull(message = "branchId is required")
        Long branchId,

        Long guideId,

        @NotNull(message = "startDatetime is required")
        OffsetDateTime startDatetime,

        @NotNull(message = "endDatetime is required")
        @Future(message = "endDatetime must be in the future")
        OffsetDateTime endDatetime,

        @NotNull(message = "maxParticipants is required")
        @Min(value = 1, message = "maxParticipants must be at least 1")
        Integer maxParticipants
) {}
