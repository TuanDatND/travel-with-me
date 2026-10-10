package com.coc.sba_treektour.event.dto;

import com.coc.sba_treektour.event.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class EventDtos {
    /** Ngăn khởi tạo lớp bao chứa các DTO của module sự kiện. */
    private EventDtos() {}

    public record DetailsInput(
            @Positive Integer durationMinutes,
            DifficultyLevel difficultyLevel,
            @Size(max = 10000) String meetingPoint,
            @Size(max = 10000) String requirements) {}

    public record EventInput(
            @NotBlank @Size(max = 200) String eventName,
            @NotNull EventType eventType,
            @Size(max = 50000) String description,
            @Size(max = 10000) String location,
            @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal basePrice,
            @Valid DetailsInput details) {}

    public record EventStatusInput(@NotNull EventStatus status) {}

    public record ImageDescriptionInput(@Size(max = 1000) String description) {}

    public record ImageResponse(Long imageId, String imageUrl, String description) {}

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
            DetailsInput details,
            List<ImageResponse> images) {}

    public record EventSummary(
            Long eventId,
            String eventName,
            EventType eventType,
            String location,
            BigDecimal basePrice,
            EventStatus status,
            DifficultyLevel difficultyLevel,
            String coverImageUrl) {}

    public record EventReference(Long eventId, EventStatus status, BigDecimal basePrice) {}
}
