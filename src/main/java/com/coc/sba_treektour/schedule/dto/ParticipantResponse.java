package com.coc.sba_treektour.schedule.dto;

import com.coc.sba_treektour.schedule.entity.CheckInStatus;
import com.coc.sba_treektour.schedule.entity.ParticipantStatus;
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
        String note
) {}
