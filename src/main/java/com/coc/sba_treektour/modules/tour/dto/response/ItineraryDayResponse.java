package com.coc.sba_treektour.modules.tour.dto.response;

import java.util.List;

public record ItineraryDayResponse(
        int day,
        String title,
        String summary,
        String distanceKm,
        String elevation,
        List<String> highlights,
        List<ActivityResponse> activities) {}
