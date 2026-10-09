package com.coc.sba_treektour.tour.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.tour.dto.TourDtos.*;
import com.coc.sba_treektour.tour.entity.*;
import com.coc.sba_treektour.tour.service.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
public class PublicTourController {
    private final EventService events;
    private final GuideService guides;

    /** Khởi tạo controller đọc tour và hướng dẫn viên dành cho khách. */
    public PublicTourController(EventService events, GuideService guides) {
        this.events = events;
        this.guides = guides;
    }

    /** Nhận bộ lọc và phân trang danh sách tour công bố; không yêu cầu đăng nhập. */
    @GetMapping("/events")
    public ApiResponse<PageResponse<EventSummary>> events(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EventType eventType,
            @RequestParam(required = false) DifficultyLevel difficultyLevel,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                events.list(
                        keyword,
                        eventType,
                        difficultyLevel,
                        minPrice,
                        maxPrice,
                        null,
                        false,
                        page,
                        size));
    }

    /** Trả nội dung, giá và ảnh của một tour công khai. */
    @GetMapping("/events/{eventId}")
    public ApiResponse<EventResponse> event(@PathVariable Long eventId) {
        return ApiResponse.success(events.get(eventId, false));
    }

    /** Trả hồ sơ hướng dẫn viên công khai, có thể lọc theo chuyên môn. */
    @GetMapping("/guides")
    public ApiResponse<PageResponse<PublicGuideResponse>> guides(
            @RequestParam(required = false) String specialization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(guides.listPublic(specialization, page, size));
    }

    /** Trả thông tin nghề nghiệp của một hướng dẫn viên đang hoạt động. */
    @GetMapping("/guides/{guideId}")
    public ApiResponse<PublicGuideResponse> guide(@PathVariable Long guideId) {
        return ApiResponse.success(guides.getPublic(guideId));
    }
}
