package com.coc.sba_treektour.modules.schedule.security;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.schedule.entity.EventParticipant;
import com.coc.sba_treektour.modules.schedule.entity.EventSchedule;
import com.coc.sba_treektour.modules.schedule.repository.ScheduleIntegrationRepository;
import com.coc.sba_treektour.modules.tour.entity.GuideStatus;
import com.coc.sba_treektour.modules.tour.service.GuideService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ScheduleAccessPolicy {
    private final GuideService guides;
    private final ScheduleIntegrationRepository integration;

    public ScheduleAccessPolicy(GuideService guides, ScheduleIntegrationRepository integration) {
        this.guides = guides;
        this.integration = integration;
    }

    public Long userId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)
                || !user.isEnabled() || !user.isAccountNonLocked())
            throw new AuthenticationCredentialsNotFoundException("Cần đăng nhập bằng tài khoản hoạt động");
        return user.getId();
    }

    public boolean hasRole(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User user
                && user.isEnabled() && user.isAccountNonLocked()
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    public boolean manages(Long branchId) {
        return hasRole("ADMIN") || hasRole("STAFF") && integration.staffAssigned(userId(), branchId);
    }

    public void requireManager(Long branchId) {
        userId();
        if (!manages(branchId)) throw new AccessDeniedException("Chỉ được quản lý lịch/vé của chi nhánh được phân công");
    }

    public void requireCustomer() {
        userId();
        if (!hasRole("CUSTOMER")) throw new AccessDeniedException("Chỉ CUSTOMER được đăng ký tour");
    }

    public void requireOwnerOrManager(EventParticipant participant) {
        if (!participant.getUserId().equals(userId())) requireManager(participant.getSchedule().getBranchId());
    }

    public void requireAssignedGuideOrManager(EventSchedule schedule) {
        userId();
        if (manages(schedule.getBranchId())) return;
        if (hasRole("GUIDE") && schedule.getGuideId() != null) {
            var guide = guides.reference(schedule.getGuideId());
            if (guide.userId().equals(userId()) && guide.accountActive() && guide.status() == GuideStatus.ACTIVE) return;
        }
        throw new AccessDeniedException("Chỉ guide được phân công hoặc quản lý đúng chi nhánh được thao tác");
    }
}
