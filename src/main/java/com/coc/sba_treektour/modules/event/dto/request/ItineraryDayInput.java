package com.coc.sba_treektour.modules.event.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ItineraryDayInput(
        @NotNull @Positive @Max(30) Integer day,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String summary,
        @Size(max = 100) String distanceKm,
        @Size(max = 100) String elevation,
        @Size(max = 20) List<@NotBlank @Size(max = 300) String> highlights,
        @Size(max = 50) List<@NotNull @Valid ActivityInput> activities) {

    /** Chuẩn hóa danh sách bị bỏ trống để service không phải xử lý null. */
    public ItineraryDayInput {
        highlights = highlights == null ? List.of() : highlights;
        activities = activities == null ? List.of() : activities;
    }
}
