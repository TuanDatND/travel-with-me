package com.coc.sba_treektour.modules.tour.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.tour.dto.response.EventResponse;
import com.coc.sba_treektour.modules.tour.dto.response.EventSummary;
import com.coc.sba_treektour.modules.tour.enums.DifficultyLevel;
import com.coc.sba_treektour.modules.tour.enums.EventType;
import com.coc.sba_treektour.modules.tour.service.EventService;
import com.coc.sba_treektour.modules.tour.dto.response.PageResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublicEventController {
    private final EventService events;

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
