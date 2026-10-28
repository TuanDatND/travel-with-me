package com.coc.sba_treektour.modules.schedule.dto.response;

import com.coc.sba_treektour.modules.schedule.enums.CheckInStatus;
import com.coc.sba_treektour.modules.schedule.enums.ParticipantStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ParticipantResponse(
        Long id,
        Long scheduleId,
        Long userId,
        OffsetDateTime registrationDate,
        ParticipantStatus participantStatus,
        CheckInStatus checkInStatus,
        BigDecimal registeredPrice,
        String note,
        UUID registrationReference,
        OffsetDateTime expiresAt
) {}
