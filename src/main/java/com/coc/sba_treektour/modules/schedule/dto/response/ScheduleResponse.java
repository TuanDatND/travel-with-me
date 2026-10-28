package com.coc.sba_treektour.modules.schedule.dto.response;

import com.coc.sba_treektour.modules.schedule.enums.ScheduleStatus;
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
