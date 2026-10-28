package com.coc.sba_treektour.modules.tour.dto.response;

import com.coc.sba_treektour.modules.tour.enums.DifficultyLevel;
import com.coc.sba_treektour.modules.tour.enums.EventStatus;
import com.coc.sba_treektour.modules.tour.enums.EventType;

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
