package com.coc.sba_treektour.modules.schedule.dto;

import com.coc.sba_treektour.modules.schedule.entity.ScheduleStatus;
import java.time.OffsetDateTime;

public record ScheduleResponse(
        Long id,
        Long eventId,
        Long branchId,
        Long guideId,
        OffsetDateTime startDatetime,
        OffsetDateTime endDatetime,
        Integer maxParticipants,
        Integer currentParticipants,
        ScheduleStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime registrationClosesAt
) {}
