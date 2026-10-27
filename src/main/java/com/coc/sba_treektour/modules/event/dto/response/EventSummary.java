package com.coc.sba_treektour.modules.event.dto.response;

import com.coc.sba_treektour.modules.event.enums.DifficultyLevel;
import com.coc.sba_treektour.modules.event.enums.EventStatus;
import com.coc.sba_treektour.modules.event.enums.EventType;

import java.math.BigDecimal;

public record EventSummary(
        Long eventId,
        String eventName,
        EventType eventType,
        String location,
        BigDecimal basePrice,
        EventStatus status,
        DifficultyLevel difficultyLevel,
        String coverImageUrl) {}
