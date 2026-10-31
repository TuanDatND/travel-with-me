package com.coc.sba_treektour.modules.branch.service.impl;

import com.coc.sba_treektour.common.security.Roles;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.enums.UserStatus;
import com.coc.sba_treektour.modules.account.exception.UserNotFoundException;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.dto.request.StaffRequest;
import com.coc.sba_treektour.modules.branch.dto.response.StaffResponse;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.entity.Staff;
import com.coc.sba_treektour.modules.branch.enums.BranchStatus;
import com.coc.sba_treektour.modules.branch.enums.StaffStatus;
import com.coc.sba_treektour.modules.branch.exception.BranchNotFoundException;
import com.coc.sba_treektour.modules.branch.exception.InvalidBranchOperationException;
import com.coc.sba_treektour.modules.branch.exception.StaffNotFoundException;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.branch.repository.StaffRepository;
import com.coc.sba_treektour.modules.branch.service.StaffService;
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

    @Override
    @Transactional(readOnly = true)
    public List<StaffResponse> getAllStaff() {
        return staffRepository.findAll().stream()
                .map(this::mapToStaffResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffResponse> getStaffByBranch(Long branchId) {
        return staffRepository.findByBranchId(branchId).stream()
                .map(this::mapToStaffResponse)
                .collect(Collectors.toList());
    }

    @Override
    public StaffResponse assignStaff(StaffRequest request) {
        if (staffRepository.existsByUserId(request.getUserId())) {
            throw new InvalidBranchOperationException("User is already assigned as a staff member");
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new BranchNotFoundException("Branch not found with id: " + request.getBranchId()));

        if (branch.getStatus() != null && !BranchStatus.ACTIVE.name().equals(branch.getStatus())) {
            throw new InvalidBranchOperationException("Cannot assign staff to an inactive branch!");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + request.getUserId()));

        if (user.getRole() == null || !Roles.STAFF.equals(user.getRole().getName())) {
            throw new InvalidBranchOperationException("Only users with the STAFF role can be assigned to a branch!");
        }

        if (user.getStatus() != null && !UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new InvalidBranchOperationException("Cannot assign a blocked or inactive user account!");
        }

        if (user.getId() != null && userRepository.hasActiveGuideProfile(user.getId())) {
            throw new InvalidBranchOperationException("This user is currently an active Tour Guide (GUIDE) and cannot be assigned as branch staff!");
        }

        String status = StaffStatus.ACTIVE.name();
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            status = validateStaffStatus(request.getStatus());
        }

        Staff staff = Staff.builder()
                .branch(branch)
                .user(user)
                .position(request.getPosition())
                .status(status)
                .build();

        Staff savedStaff = staffRepository.save(staff);
        return mapToStaffResponse(savedStaff);
    }

    @Override
    public StaffResponse updateStaff(Long id, StaffRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new StaffNotFoundException("Staff not found with id: " + id));

        if (!staff.getBranch().getId().equals(request.getBranchId())) {
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(() -> new BranchNotFoundException("Branch not found with id: " + request.getBranchId()));
            if (branch.getStatus() != null && !BranchStatus.ACTIVE.name().equals(branch.getStatus())) {
                throw new InvalidBranchOperationException("Cannot transfer staff to an inactive branch!");
            }
            staff.setBranch(branch);
        }

        staff.setPosition(request.getPosition());

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            staff.setStatus(validateStaffStatus(request.getStatus()));
        }

        Staff updatedStaff = staffRepository.save(staff);
        return mapToStaffResponse(updatedStaff);
    }

    @Override
    public StaffResponse updateStaffStatus(Long id, String status) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new StaffNotFoundException("Staff not found with id: " + id));

        String upperStatus = validateStaffStatus(status);
        staff.setStatus(upperStatus);
        Staff updatedStaff = staffRepository.save(staff);
        return mapToStaffResponse(updatedStaff);
    }

    @Override
    public void deleteStaff(Long id) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new StaffNotFoundException("Staff not found with id: " + id));
        staffRepository.delete(staff);
    }

    private String validateStaffStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new InvalidBranchOperationException("Invalid status. Must be ACTIVE or INACTIVE");
        }
        String upperStatus = status.toUpperCase();
        try {
            StaffStatus.valueOf(upperStatus);
            return upperStatus;
        } catch (IllegalArgumentException e) {
            throw new InvalidBranchOperationException("Invalid status. Must be ACTIVE or INACTIVE");
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
