package com.coc.sba_treektour.modules.branch.service;

import com.coc.sba_treektour.common.security.Roles;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.branch.entity.Staff;
import com.coc.sba_treektour.modules.branch.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra phạm vi chi nhánh của người đang đăng nhập.
 *
 * - ADMIN: truy cập mọi chi nhánh.
 * - BRANCH_ADMIN / STAFF: chỉ truy cập chi nhánh được gán trong bảng staff (và phải ACTIVE).
 *
 * Các module khác (order, inventory, schedule...) có thể inject class này để giới hạn dữ liệu theo chi nhánh.
 */
@Component
@RequiredArgsConstructor
public class BranchAccessService {

    private final StaffRepository staffRepository;

    /** User đang đăng nhập (principal do JwtAuthenticationFilter đặt vào). */
    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new AccessDeniedException("Bạn chưa đăng nhập");
        }
        return user;
    }

    public boolean isAdmin() {
        return hasRole(currentUser(), Roles.ADMIN);
    }

    public boolean isBranchAdmin() {
        return hasRole(currentUser(), Roles.BRANCH_ADMIN);
    }

    /** Chỉ ADMIN tổng được đi tiếp. */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Chỉ ADMIN tổng mới có quyền thực hiện thao tác này");
        }
    }

    /** Id chi nhánh của user hiện tại. Dùng cho BRANCH_ADMIN / STAFF; ADMIN không thuộc chi nhánh nào. */
    @Transactional(readOnly = true)
    public Long currentBranchId() {
        User user = currentUser();
        Staff staff = staffRepository.findByUserId(user.getId())
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .orElseThrow(() -> new AccessDeniedException("Tài khoản chưa được gán vào chi nhánh nào"));
        return staff.getBranch().getId();
    }

    /** ADMIN luôn qua; người khác phải thuộc đúng chi nhánh {@code branchId}. */
    @Transactional(readOnly = true)
    public void requireBranchAccess(Long branchId) {
        if (isAdmin()) {
            return;
        }
        if (branchId == null || !branchId.equals(currentBranchId())) {
            throw new AccessDeniedException("Bạn không có quyền trên chi nhánh này");
        }
    }

    private boolean hasRole(User user, String role) {
        return user.getRole() != null && role.equals(user.getRole().getName());
    }
}
