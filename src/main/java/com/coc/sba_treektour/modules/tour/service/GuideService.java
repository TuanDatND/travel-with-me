package com.coc.sba_treektour.modules.tour.service;

import com.coc.sba_treektour.modules.tour.dto.request.GuideCreateInput;
import com.coc.sba_treektour.modules.tour.dto.request.GuideUpdateInput;
import com.coc.sba_treektour.modules.tour.dto.response.AdminGuideResponse;
import com.coc.sba_treektour.modules.tour.dto.response.GuideReference;
import com.coc.sba_treektour.modules.tour.dto.response.PageResponse;
import com.coc.sba_treektour.modules.tour.dto.response.PublicGuideResponse;
import com.coc.sba_treektour.modules.tour.enums.GuideStatus;

import org.springframework.web.multipart.MultipartFile;

public interface GuideService {
    /**
     * Phân trang hồ sơ ACTIVE có tài khoản ACTIVE; đọc tên tài khoản theo lô để tránh truy vấn lặp.
     */
    PageResponse<PublicGuideResponse> listPublic(String specialization, int page, int size);

    /** Lọc hồ sơ quản trị theo chuyên môn, trạng thái hoặc userId. */
    PageResponse<AdminGuideResponse> listAdmin(
            String specialization, GuideStatus status, Long userId, int page, int size);

    /** Lấy hồ sơ công khai; trả 404 nếu hồ sơ hoặc tài khoản đã ngừng hoạt động. */
    PublicGuideResponse getPublic(Long id);

    /**
     * Lấy hồ sơ quản trị, gồm userId và trạng thái nhưng không chứa thông tin tài khoản nhạy cảm.
     */
    AdminGuideResponse getAdmin(Long id);

    /** Trả guideId, userId và trạng thái để module lịch kiểm tra phân công theo cùng một người. */
    GuideReference reference(Long id);

    /**
     * Tạo hồ sơ cho tài khoản ACTIVE; unique index của DB chặn chuyên môn trùng trên cùng userId.
     */
    AdminGuideResponse create(GuideCreateInput input);

    /** Cập nhật kinh nghiệm và chuyên môn; giữ nguyên userId và để DB chặn chuyên môn trùng. */
    AdminGuideResponse update(Long id, GuideUpdateInput input);

    /** Kích hoạt hoặc ngừng hồ sơ; chỉ kích hoạt khi tài khoản liên kết đang hoạt động. */
    AdminGuideResponse status(Long id, GuideStatus status);

    /** Upload ảnh mới; chỉ xóa ảnh cũ sau commit, dọn ảnh mới nếu giao dịch rollback. */
    AdminGuideResponse uploadAvatar(Long id, MultipartFile file);

    /** Xóa tài sản trước metadata; nếu Cloudinary lỗi thì giữ nguyên thông tin ảnh trong DB. */
    void deleteAvatar(Long id);
}
