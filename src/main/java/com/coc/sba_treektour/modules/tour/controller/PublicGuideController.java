package com.coc.sba_treektour.modules.tour.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.tour.dto.response.PageResponse;
import com.coc.sba_treektour.modules.tour.dto.response.PublicGuideResponse;
import com.coc.sba_treektour.modules.tour.service.GuideService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublicGuideController {
    private final GuideService guides;

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
