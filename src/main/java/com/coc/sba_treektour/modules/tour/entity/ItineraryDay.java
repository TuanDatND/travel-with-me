package com.coc.sba_treektour.modules.tour.entity;

import java.util.List;

/** Nội dung một ngày trong JSONB; không phải lịch khởi hành hay phân công hướng dẫn viên. */
public record ItineraryDay(
        int day,
        String title,
        String summary,
        String distanceKm,
        String elevation,
        List<String> highlights,
        List<Activity> activities) {

    public record Activity(
            String time, String title, String location, String description, String icon) {}
}
