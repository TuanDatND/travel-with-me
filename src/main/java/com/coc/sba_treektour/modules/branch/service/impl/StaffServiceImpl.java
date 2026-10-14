package com.coc.sba_treektour.modules.branch.service.impl;

import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.dto.StaffRequest;
import com.coc.sba_treektour.modules.branch.dto.StaffResponse;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.entity.Staff;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.branch.repository.StaffRepository;
import com.coc.sba_treektour.modules.branch.service.BranchAccessService;
import com.coc.sba_treektour.modules.branch.service.StaffService;
import com.coc.sba_treektour.common.security.Roles;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StaffServiceImpl implements StaffService {

    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final BranchAccessService branchAccess;

    @Override
    public List<StaffResponse> getStaffByBranch(Long branchId) {
        branchAccess.requireBranchAccess(branchId);
        return staffRepository.findByBranchId(branchId).stream()
                .map(this::mapToStaffResponse)
                .collect(Collectors.toList());
    }

    @Override
    public StaffResponse assignStaff(StaffRequest request) {
        branchAccess.requireBranchAccess(request.getBranchId());

        if (staffRepository.existsByUserId(request.getUserId())) {
            throw new RuntimeException("User is already assigned as a staff member");
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));
        
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        String targetRole = user.getRole() != null ? user.getRole().getName() : null;
        if (!Roles.STAFF.equals(targetRole) && !Roles.BRANCH_ADMIN.equals(targetRole)) {
            throw new RuntimeException("Chỉ có thể gán tài khoản có vai trò STAFF hoặc BRANCH_ADMIN vào chi nhánh");
        }
        // BRANCH_ADMIN chỉ được gán STAFF; việc bổ nhiệm BRANCH_ADMIN thuộc ADMIN tổng
        if (Roles.BRANCH_ADMIN.equals(targetRole)) {
            branchAccess.requireAdmin();
        }

        Staff staff = Staff.builder()
                .branch(branch)
                .user(user)
                .position(request.getPosition())
                .status("ACTIVE")
                .build();
        
        Staff savedStaff = staffRepository.save(staff);
        return mapToStaffResponse(savedStaff);
    }

    @Override
    public StaffResponse updateStaff(Long id, StaffRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found with id: " + id));
        requireCanManage(staff);

        if (!staff.getBranch().getId().equals(request.getBranchId())) {
            // Chuyển chi nhánh ảnh hưởng 2 chi nhánh, chỉ ADMIN tổng được làm
            branchAccess.requireAdmin();
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));
            staff.setBranch(branch);
        }

        staff.setPosition(request.getPosition());
        
        Staff updatedStaff = staffRepository.save(staff);
        return mapToStaffResponse(updatedStaff);
    }

    @Override
    public StaffResponse updateStaffStatus(Long id, String status) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Staff not found with id: " + id));
        requireCanManage(staff);

        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new RuntimeException("Invalid status. Must be ACTIVE or INACTIVE");
        }

        staff.setStatus(status);
        Staff updatedStaff = staffRepository.save(staff);
        return mapToStaffResponse(updatedStaff);
    }

    /**
     * ADMIN quản lý mọi nhân viên. BRANCH_ADMIN chỉ quản lý STAFF thuộc chi nhánh mình,
     * không được sửa BRANCH_ADMIN (kể cả chính mình).
     */
    private void requireCanManage(Staff target) {
        branchAccess.requireBranchAccess(target.getBranch().getId());
        if (!branchAccess.isAdmin()) {
            String targetRole = target.getUser().getRole() != null ? target.getUser().getRole().getName() : null;
            if (!Roles.STAFF.equals(targetRole)) {
                throw new AccessDeniedException("Chỉ ADMIN tổng mới được quản lý admin chi nhánh");
            }
        }
    }

    @Override
    public StaffResponse mapToStaffResponse(Staff staff) {
        return StaffResponse.builder()
                .id(staff.getId())
                .branchId(staff.getBranch().getId())
                .branchName(staff.getBranch().getName())
                .userId(staff.getUser().getId())
                .userName(staff.getUser().getFullName())
                .position(staff.getPosition())
                .status(staff.getStatus())
                .build();
    }
}
