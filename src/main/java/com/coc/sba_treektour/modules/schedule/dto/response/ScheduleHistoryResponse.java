package com.coc.sba_treektour.modules.schedule.dto.response;

import java.time.OffsetDateTime;

public record ScheduleHistoryResponse(
        Long id,
        Long scheduleId,
        OffsetDateTime oldStartDatetime,
        OffsetDateTime newStartDatetime,
        OffsetDateTime oldEndDatetime,
        OffsetDateTime newEndDatetime,
        String oldStatus,
        String newStatus,
        String changeReason,
        OffsetDateTime changedAt,
        Long changedBy
) {}
