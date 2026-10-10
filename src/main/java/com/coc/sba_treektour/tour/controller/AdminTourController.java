package com.coc.sba_treektour.tour.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.tour.dto.TourDtos.*;
import com.coc.sba_treektour.tour.entity.*;
import com.coc.sba_treektour.tour.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/admin")
public class AdminTourController {
    private final EventService events;
    private final GuideService guides;

    /** Khởi tạo controller quản trị tour và hướng dẫn viên; filter bảo mật yêu cầu ADMIN. */
    public AdminTourController(EventService events, GuideService guides) {
        this.events = events;
        this.guides = guides;
    }

    /** Lấy danh sách tour quản trị với bộ lọc nội dung, giá và trạng thái. */
    @GetMapping("/events")
    public ApiResponse<PageResponse<EventSummary>> events(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) DifficultyLevel difficultyLevel,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) EventStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                events.list(
                        keyword,
                        eventType,
                        difficultyLevel,
                        minPrice,
                        maxPrice,
                        status,
                        true,
                        page,
                        size));
    }

    /** Lấy chi tiết tour ở mọi trạng thái cho quản trị viên. */
    @GetMapping("/events/{eventId}")
    public ApiResponse<EventResponse> event(@PathVariable Long eventId) {
        return ApiResponse.success(events.get(eventId, true));
    }

    /** Kiểm tra request và tạo tour nháp; trả HTTP 201 khi thành công. */
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<EventResponse> create(@Valid @RequestBody EventInput input) {
        return ApiResponse.created("Tạo tour thành công", events.create(input));
    }

    /** Kiểm tra request và thay thế nội dung, chi tiết của tour được phép sửa. */
    @PutMapping("/events/{eventId}")
    public ApiResponse<EventResponse> update(
            @PathVariable Long eventId, @Valid @RequestBody EventInput input) {
        return ApiResponse.success(events.update(eventId, input));
    }

    /** Nhận yêu cầu công bố hoặc lưu trữ tour và chuyển cho service kiểm tra nghiệp vụ. */
    @PatchMapping("/events/{eventId}/status")
    public ApiResponse<EventResponse> status(
            @PathVariable Long eventId, @Valid @RequestBody EventStatusInput input) {
        return ApiResponse.success(events.status(eventId, input.status()));
    }

    /** Nhận file multipart cùng mô tả tùy chọn và trả metadata ảnh đã lưu. */
    @PostMapping(value = "/events/{eventId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImageResponse> upload(
            @PathVariable Long eventId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String description) {
        return ApiResponse.created("Tải ảnh thành công", events.upload(eventId, file, description));
    }

    /** Kiểm tra request và cập nhật mô tả ảnh trong tour chỉ định. */
    @PatchMapping("/events/{eventId}/images/{imageId}")
    public ApiResponse<ImageResponse> describe(
            @PathVariable Long eventId,
            @PathVariable Long imageId,
            @Valid @RequestBody ImageDescriptionInput input) {
        return ApiResponse.success(events.describe(eventId, imageId, input.description()));
    }

    /** Xóa ảnh thuộc tour chỉ định; trả HTTP 204 khi thành công. */
    @DeleteMapping("/events/{eventId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long eventId, @PathVariable Long imageId) {
        events.deleteImage(eventId, imageId);
    }

    /** Lấy hồ sơ quản trị theo chuyên môn, trạng thái hoặc tài khoản. */
    @GetMapping("/guides")
    public ApiResponse<PageResponse<AdminGuideResponse>> guides(
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) GuideStatus status,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(guides.listAdmin(specialization, status, userId, page, size));
    }

    /** Lấy chi tiết quản trị của một hồ sơ hướng dẫn viên. */
    @GetMapping("/guides/{guideId}")
    public ApiResponse<AdminGuideResponse> guide(@PathVariable Long guideId) {
        return ApiResponse.success(guides.getAdmin(guideId));
    }

    /** Tạo hồ sơ chuyên môn từ tài khoản có sẵn sau khi kiểm tra request. */
    @PostMapping("/guides")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminGuideResponse> createGuide(@Valid @RequestBody GuideCreateInput input) {
        return ApiResponse.created("Tạo hồ sơ hướng dẫn viên thành công", guides.create(input));
    }

    /** Cập nhật kinh nghiệm và chuyên môn; không cho đổi tài khoản liên kết. */
    @PutMapping("/guides/{guideId}")
    public ApiResponse<AdminGuideResponse> updateGuide(
            @PathVariable Long guideId, @Valid @RequestBody GuideUpdateInput input) {
        return ApiResponse.success(guides.update(guideId, input));
    }

    /** Kích hoạt hoặc ngừng hoạt động một hồ sơ hướng dẫn viên. */
    @PatchMapping("/guides/{guideId}/status")
    public ApiResponse<AdminGuideResponse> guideStatus(
            @PathVariable Long guideId, @Valid @RequestBody GuideStatusInput input) {
        return ApiResponse.success(guides.status(guideId, input.status()));
    }
}
