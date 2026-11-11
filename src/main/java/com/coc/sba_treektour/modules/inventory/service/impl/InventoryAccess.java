package com.coc.sba_treektour.modules.inventory.service.impl;

import com.coc.sba_treektour.common.exception.CatalogException;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.branch.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @RequiredArgsConstructor
class InventoryAccess {
    private final StaffRepository staff;
    @Transactional(readOnly = true)
    public void requireBranch(Long branchId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new CatalogException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập");
        if (auth.getPrincipal() instanceof User user && (!user.isEnabled() || !user.isAccountNonLocked()))
            throw new CatalogException(HttpStatus.FORBIDDEN, "Tài khoản không hoạt động");
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN"))) return;
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("STAFF"))
                && auth.getPrincipal() instanceof User user
                && staff.findByUserId(user.getId()).filter(s -> s.getStatus().equals("ACTIVE")
                    && s.getBranch().getId().equals(branchId) && s.getBranch().getStatus().equals("ACTIVE")).isPresent()) return;
        throw new CatalogException(HttpStatus.FORBIDDEN, "Chỉ được quản lý kho của chi nhánh được phân công");
    }
}
