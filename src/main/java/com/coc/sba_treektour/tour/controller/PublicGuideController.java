package com.coc.sba_treektour.tour.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.tour.dto.TourDtos.*;
import com.coc.sba_treektour.tour.entity.*;
import com.coc.sba_treektour.tour.service.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PublicGuideController {
    private final GuideService guides;

    /** Khởi tạo controller xử lý hồ sơ hướng dẫn viên. */
    public PublicGuideController(GuideService guides) {
        this.guides = guides;
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
