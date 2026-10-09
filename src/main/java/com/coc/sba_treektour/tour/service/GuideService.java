package com.coc.sba_treektour.tour.service;

import com.coc.sba_treektour.tour.dto.TourDtos.*;
import com.coc.sba_treektour.tour.entity.*;
import com.coc.sba_treektour.tour.repository.GuideRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class GuideService {
    private final GuideRepository guides;
    private final AccountReader accounts;

    /** Khởi tạo dịch vụ hướng dẫn viên và bộ đọc tài khoản. */
    public GuideService(GuideRepository guides, AccountReader accounts) {
        this.guides = guides;
        this.accounts = accounts;
    }

    /** Phân trang hồ sơ ACTIVE có tài khoản ACTIVE; đọc tên tài khoản theo lô để tránh truy vấn lặp. */
    public PageResponse<PublicGuideResponse> listPublic(String specialization, int page, int size) {
        var result =
                guides.findPublic(
                        TourQueries.contains(specialization),
                        TourQueries.page(page, size, Sort.by(Sort.Direction.DESC, "guide_id")));
        var users = accounts.findAll(result.stream().map(TourGuide::getUserId).distinct().toList());
        return PageResponse.of(result.map(g -> publicResponse(g, users.get(g.getUserId()))));
    }

    /** Lọc hồ sơ quản trị theo chuyên môn, trạng thái hoặc userId. */
    public PageResponse<AdminGuideResponse> listAdmin(
            String specialization, GuideStatus status, Long userId, int page, int size) {
        String pattern = TourQueries.contains(specialization);
        Specification<TourGuide> spec =
                (root, query, cb) -> {
                    var p = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    if (pattern != null)
                        p.add(cb.like(cb.lower(root.get("specialization")), pattern, '!'));
                    if (status != null) p.add(cb.equal(root.get("status"), status));
                    if (userId != null) p.add(cb.equal(root.get("userId"), userId));
                    return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
                };
        var result =
                guides.findAll(
                        spec, TourQueries.page(page, size, Sort.by(Sort.Direction.DESC, "id")));
        var users = accounts.findAll(result.stream().map(TourGuide::getUserId).distinct().toList());
        return PageResponse.of(result.map(g -> adminResponse(g, users.get(g.getUserId()))));
    }

    /** Lấy hồ sơ công khai; trả 404 nếu hồ sơ hoặc tài khoản đã ngừng hoạt động. */
    public PublicGuideResponse getPublic(Long id) {
        TourGuide g = find(id);
        var user = accounts.find(g.getUserId());
        if (g.getStatus() != GuideStatus.ACTIVE || !user.active()) throw TourException.missing();
        return publicResponse(g, user);
    }

    /** Lấy hồ sơ quản trị, gồm userId và trạng thái nhưng không chứa thông tin tài khoản nhạy cảm. */
    public AdminGuideResponse getAdmin(Long id) {
        TourGuide g = find(id);
        return adminResponse(g, accounts.find(g.getUserId()));
    }

    /** Trả guideId, userId và trạng thái để module lịch kiểm tra phân công theo cùng một người. */
    public GuideReference reference(Long id) {
        TourGuide g = find(id);
        return new GuideReference(
                g.getId(), g.getUserId(), g.getStatus(), accounts.find(g.getUserId()).active());
    }

    /** Tạo hồ sơ cho tài khoản ACTIVE; unique index của DB chặn chuyên môn trùng trên cùng userId. */
    @Transactional
    public AdminGuideResponse create(GuideCreateInput input) {
        var user = accounts.requireActive(input.userId());
        // ponytail: unique index chặn chuyên môn trùng, kể cả khi ghi đồng thời.
        TourGuide g = new TourGuide();
        g.setUserId(input.userId());
        g.setExperienceYears(input.experienceYears());
        g.setSpecialization(input.specialization().trim());
        return adminResponse(guides.saveAndFlush(g), user);
    }

    /** Cập nhật kinh nghiệm và chuyên môn; giữ nguyên userId và để DB chặn chuyên môn trùng. */
    @Transactional
    public AdminGuideResponse update(Long id, GuideUpdateInput input) {
        TourGuide g = find(id);
        g.setExperienceYears(input.experienceYears());
        g.setSpecialization(input.specialization().trim());
        guides.flush();
        return adminResponse(g, accounts.find(g.getUserId()));
    }

    /** Kích hoạt hoặc ngừng hồ sơ; chỉ kích hoạt khi tài khoản liên kết đang hoạt động. */
    @Transactional
    public AdminGuideResponse status(Long id, GuideStatus status) {
        TourGuide g = find(id);
        var user =
                status == GuideStatus.ACTIVE
                        ? accounts.requireActive(g.getUserId())
                        : accounts.find(g.getUserId());
        g.setStatus(status);
        guides.flush();
        return adminResponse(g, user);
    }

    /** Tìm hồ sơ theo guideId hoặc trả lỗi 404. */
    private TourGuide find(Long id) {
        return guides.findById(id).orElseThrow(TourException::missing);
    }

    /** Tạo hồ sơ công khai chỉ gồm tên hiển thị và thông tin nghề nghiệp. */
    private PublicGuideResponse publicResponse(TourGuide g, AccountReader.Account u) {
        return new PublicGuideResponse(
                g.getId(), u.displayName(), g.getExperienceYears(), g.getSpecialization());
    }

    /** Tạo hồ sơ quản trị với userId và trạng thái; không trả dữ liệu riêng tư của tài khoản. */
    private AdminGuideResponse adminResponse(TourGuide g, AccountReader.Account u) {
        return new AdminGuideResponse(
                g.getId(),
                g.getUserId(),
                u.displayName(),
                g.getExperienceYears(),
                g.getSpecialization(),
                g.getStatus());
    }
}
