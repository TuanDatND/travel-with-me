package com.coc.sba_treektour.schedule.service;

import com.coc.sba_treektour.account.entity.User;
import com.coc.sba_treektour.schedule.entity.EventSchedule;
import com.coc.sba_treektour.tour.entity.GuideStatus;
import com.coc.sba_treektour.tour.service.GuideService;
import com.coc.sba_treektour.tour.service.TourException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Kiểm tra quyền lịch/vé bằng principal JWT và hồ sơ guide hiện có. */
@Component
public class ScheduleAccess {
    private final GuideService guides;

    /** Tái sử dụng dịch vụ guide để xác định người được phân công. */
    public ScheduleAccess(GuideService guides) {
        this.guides = guides;
    }

    /** Nhận diện quyền quản lý từ authority chuẩn của member 1. */
    public boolean manager() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getAuthorities().stream()
                        .anyMatch(
                                a ->
                                        a.getAuthority().equals("ADMIN")
                                                || a.getAuthority().equals("STAFF"));
    }

    /** Lấy tài khoản thật từ JWT; không nhận userId do client tự khai. */
    public Long userId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !(auth.getPrincipal() instanceof User user)
                || !user.isEnabled()
                || !user.isAccountNonLocked())
            throw new TourException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Login required");
        return user.getId();
    }

    /** Chỉ chủ vé hoặc quản lý được đọc/hủy vé của người dùng. */
    public void requireOwnerOrManager(Long ownerId) {
        if (!manager() && !userId().equals(ownerId)) forbidden();
    }

    /** Cho quản lý hoặc guide ACTIVE được phân công xem danh sách và điểm danh. */
    public void requireAssignedGuideOrManager(EventSchedule schedule) {
        if (manager()) return;
        if (schedule.getGuideId() != null) {
            var guide = guides.reference(schedule.getGuideId());
            if (guide.userId().equals(userId())
                    && guide.accountActive()
                    && guide.status() == GuideStatus.ACTIVE) return;
        }
        forbidden();
    }

    /** Trả 403 thống nhất khi tài khoản không có quyền thao tác. */
    private void forbidden() {
        throw new TourException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }
}
