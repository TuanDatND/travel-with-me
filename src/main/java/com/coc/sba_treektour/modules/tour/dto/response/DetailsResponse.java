package com.coc.sba_treektour.modules.tour.dto.response;

import com.coc.sba_treektour.modules.tour.enums.DifficultyLevel;

import java.util.List;

public record DetailsResponse(
        Integer durationMinutes,
        DifficultyLevel difficultyLevel,
        String meetingPoint,
        String requirements,
        List<ItineraryDayResponse> itinerary,
        List<PackingItemResponse> packingItems) {}
