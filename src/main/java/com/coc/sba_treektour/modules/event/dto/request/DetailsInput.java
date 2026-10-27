package com.coc.sba_treektour.modules.event.dto.request;

import com.coc.sba_treektour.modules.event.enums.DifficultyLevel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DetailsInput(
        @Positive Integer durationMinutes,
        DifficultyLevel difficultyLevel,
        @Size(max = 10000) String meetingPoint,
        @Size(max = 10000) String requirements,
        @Size(max = 30) List<@NotNull @Valid ItineraryDayInput> itinerary,
        @Size(max = 100) List<@NotNull @Valid PackingItemInput> packingItems) {

    /** Giữ request cũ hợp lệ; danh sách không gửi tương ứng với nội dung chưa cập nhật. */
    public DetailsInput {
        itinerary = itinerary == null ? List.of() : itinerary;
        packingItems = packingItems == null ? List.of() : packingItems;
    }
}
