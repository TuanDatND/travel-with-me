package com.coc.sba_treektour.tour.dto;

import com.coc.sba_treektour.tour.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class TourDtos {
    /** Ngăn khởi tạo lớp bao chứa các DTO của module tour. */
    private TourDtos() {}

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

    public record GuideStatusInput(@NotNull GuideStatus status) {}

    public record GuideCreateInput(
            @NotNull @Positive Long userId,
            @NotNull @PositiveOrZero Integer experienceYears,
            @NotBlank @Size(max = 1000) String specialization) {}

    public record GuideUpdateInput(
            @NotNull @PositiveOrZero Integer experienceYears,
            @NotBlank @Size(max = 1000) String specialization) {}

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

    public record PublicGuideResponse(
            Long guideId, String displayName, Integer experienceYears, String specialization) {}

    public record AdminGuideResponse(
            Long guideId,
            Long userId,
            String displayName,
            Integer experienceYears,
            String specialization,
            GuideStatus status) {}

    public record PageResponse<T>(
            List<T> items, int page, int size, long totalElements, int totalPages) {
        /** Chuyển Page của Spring thành response phân trang ổn định cho client. */
        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(
                    page.getContent(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    public record EventReference(Long eventId, EventStatus status, BigDecimal basePrice) {}

    public record GuideReference(
            Long guideId, Long userId, GuideStatus status, boolean accountActive) {}
}
