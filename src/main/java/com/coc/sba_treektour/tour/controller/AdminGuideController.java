package com.coc.sba_treektour.tour.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.tour.dto.TourDtos.*;
import com.coc.sba_treektour.tour.entity.*;
import com.coc.sba_treektour.tour.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminGuideController {
    private final GuideService guides;

    /** Khởi tạo controller xử lý hồ sơ hướng dẫn viên. */
    public AdminGuideController(GuideService guides) {
        this.guides = guides;
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
