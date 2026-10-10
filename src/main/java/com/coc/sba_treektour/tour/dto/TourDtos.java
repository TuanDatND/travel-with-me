package com.coc.sba_treektour.tour.dto;

import com.coc.sba_treektour.tour.entity.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import java.util.List;

public final class TourDtos {
    /** Ngăn khởi tạo lớp bao chứa các DTO của module tour. */
    private TourDtos() {}

    public record GuideStatusInput(@NotNull GuideStatus status) {}

    public record GuideCreateInput(
            @NotNull @Positive Long userId,
            @NotNull @PositiveOrZero Integer experienceYears,
            @NotBlank @Size(max = 1000) String specialization) {}

    public record GuideUpdateInput(
            @NotNull @PositiveOrZero Integer experienceYears,
            @NotBlank @Size(max = 1000) String specialization) {}

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

    public record GuideReference(
            Long guideId, Long userId, GuideStatus status, boolean accountActive) {}
}
