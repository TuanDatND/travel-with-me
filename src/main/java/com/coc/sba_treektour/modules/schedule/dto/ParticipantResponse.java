package com.coc.sba_treektour.modules.schedule.dto;

import com.coc.sba_treektour.modules.schedule.entity.CheckInStatus;
import com.coc.sba_treektour.modules.schedule.entity.ParticipantStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ParticipantResponse(
        Long id,
        Long scheduleId,
        Long userId,
        OffsetDateTime registrationDate,
        ParticipantStatus participantStatus,
        CheckInStatus checkInStatus,
        BigDecimal registeredPrice,
        String note,
        java.util.UUID registrationReference,
        OffsetDateTime expiresAt
) {}
