package com.coc.sba_treektour.modules.schedule.dto;

import com.coc.sba_treektour.modules.schedule.entity.ScheduleStatus;
import java.time.OffsetDateTime;

/** Thông tin lịch được phân công; không phụ thuộc khả năng xem tour công khai. */
public record GuideScheduleResponse(
        Long id, Long eventId, String eventName, Long branchId, String branchName, Long guideId,
        OffsetDateTime startDatetime, OffsetDateTime endDatetime, Integer maxParticipants,
        Integer currentParticipants, ScheduleStatus status, OffsetDateTime createdAt,
        OffsetDateTime registrationClosesAt) {}
