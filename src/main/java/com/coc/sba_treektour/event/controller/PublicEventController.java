package com.coc.sba_treektour.event.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.event.dto.EventDtos.*;
import com.coc.sba_treektour.event.entity.*;
import com.coc.sba_treektour.event.service.*;
import org.springframework.web.bind.annotation.*;
import com.coc.sba_treektour.tour.dto.TourDtos.PageResponse;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
public class PublicEventController {
    private final EventService events;

    /** Khởi tạo controller xử lý nội dung và ảnh tour/sự kiện. */
    public PublicEventController(EventService events) {
        this.events = events;
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

}
