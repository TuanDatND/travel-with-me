package com.coc.sba_treektour.modules.tour.dto.response;

import com.coc.sba_treektour.modules.tour.enums.EventStatus;
import com.coc.sba_treektour.modules.tour.enums.EventType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record EventResponse(
        Long eventId,
        String eventName,
        EventType eventType,
        String description,
        String location,
        BigDecimal basePrice,
        EventStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        DetailsResponse details,
        List<ImageResponse> images) {}
