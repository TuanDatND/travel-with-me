package com.coc.sba_treektour.modules.tour.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.tour.dto.request.GuideCreateInput;
import com.coc.sba_treektour.modules.tour.dto.request.GuideRegistrationInput;
import com.coc.sba_treektour.modules.tour.dto.request.GuideStatusInput;
import com.coc.sba_treektour.modules.tour.dto.request.GuideUpdateInput;
import com.coc.sba_treektour.modules.tour.dto.response.AdminGuideResponse;
import com.coc.sba_treektour.modules.tour.dto.response.GuideRegistrationResponse;
import com.coc.sba_treektour.modules.tour.dto.response.PageResponse;
import com.coc.sba_treektour.modules.tour.enums.GuideStatus;
import com.coc.sba_treektour.modules.tour.service.GuideRegistrationService;
import com.coc.sba_treektour.modules.tour.service.GuideService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminGuideController {
    private final GuideService guides;
    private final GuideRegistrationService registrations;

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

    /**
     * Tạo tài khoản GUIDE và hồ sơ trong cùng giao dịch; mật khẩu tạm chỉ trả cho ADMIN ở lần tạo.
     */
    @PostMapping("/guides/with-account")
    public ResponseEntity<ApiResponse<GuideRegistrationResponse>> createWithAccount(
            @Valid @RequestBody GuideRegistrationInput input) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.created(
                                "Tạo tài khoản và hồ sơ hướng dẫn viên thành công",
                                registrations.create(input)));
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

    /**
     * Thay ảnh đại diện bằng JPEG/PNG/WebP tối đa 5 MiB; quyền ADMIN áp dụng qua security chain.
     */
    @PutMapping(value = "/guides/{guideId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminGuideResponse> uploadAvatar(
            @PathVariable Long guideId, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(guides.uploadAvatar(guideId, file));
    }

    /** Xóa ảnh đại diện hiện tại; gọi lại khi không có ảnh vẫn thành công. */
    @DeleteMapping("/guides/{guideId}/avatar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvatar(@PathVariable Long guideId) {
        guides.deleteAvatar(guideId);
    }
}
