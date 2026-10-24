package com.coc.sba_treektour.modules.tour.dto;

import com.coc.sba_treektour.modules.tour.entity.*;

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
            @NotBlank @Size(max = 1000) String specialization,
            @Size(max = 5000) String bio,
            @Size(max = 10) List<@NotBlank @Size(max = 50) String> languages) {}

    public record GuideRegistrationInput(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) String phone,
            @NotNull @PositiveOrZero Integer experienceYears,
            @NotBlank @Size(max = 1000) String specialization,
            @Size(max = 5000) String bio,
            @Size(max = 10) List<@NotBlank @Size(max = 50) String> languages) {}

    public record GuideRegistrationResponse(
            AdminGuideResponse guide, String email, String temporaryPassword, boolean emailSent) {}

    public record GuideUpdateInput(
            @NotNull @PositiveOrZero Integer experienceYears,
            @NotBlank @Size(max = 1000) String specialization,
            @Size(max = 5000) String bio,
            @Size(max = 10) List<@NotBlank @Size(max = 50) String> languages) {}

    public record PublicGuideResponse(
            Long guideId,
            String displayName,
            Integer experienceYears,
            String specialization,
            String bio,
            List<String> languages,
            String avatarUrl) {}

    public record AdminGuideResponse(
            Long guideId,
            Long userId,
            String displayName,
            Integer experienceYears,
            String specialization,
            GuideStatus status,
            String bio,
            List<String> languages,
            String avatarUrl) {}

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
