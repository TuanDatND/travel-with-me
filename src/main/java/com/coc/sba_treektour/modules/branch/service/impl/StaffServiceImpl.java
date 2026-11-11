package com.coc.sba_treektour.modules.branch.service.impl;

import com.coc.sba_treektour.common.security.Roles;
import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.enums.UserStatus;
import com.coc.sba_treektour.modules.account.exception.UserNotFoundException;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.branch.dto.request.StaffRequest;
import com.coc.sba_treektour.modules.branch.dto.response.StaffResponse;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.entity.Staff;
import com.coc.sba_treektour.modules.branch.enums.BranchStatus;
import com.coc.sba_treektour.modules.branch.enums.StaffStatus;
import com.coc.sba_treektour.modules.branch.exception.BranchConflictException;
import com.coc.sba_treektour.modules.branch.exception.BranchNotFoundException;
import com.coc.sba_treektour.modules.branch.exception.InvalidBranchOperationException;
import com.coc.sba_treektour.modules.branch.exception.StaffNotFoundException;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.branch.repository.StaffRepository;
import com.coc.sba_treektour.modules.branch.service.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class StaffServiceImpl implements StaffService {

    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Autowired
    public StaffServiceImpl(StaffRepository staffRepository,
                            BranchRepository branchRepository,
                            UserRepository userRepository,
                            RoleRepository roleRepository) {
        this.staffRepository = staffRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public StaffServiceImpl(StaffRepository staffRepository,
                            BranchRepository branchRepository,
                            UserRepository userRepository) {
        this(staffRepository, branchRepository, userRepository, null);
    }

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
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new BranchNotFoundException("Branch not found with id: " + request.getBranchId()));

        if (branch.getStatus() != null && !BranchStatus.ACTIVE.name().equals(branch.getStatus())) {
            throw new InvalidBranchOperationException("Cannot assign staff to an inactive branch!");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + request.getUserId()));

        String currentRole = user.getRole() != null ? user.getRole().getName() : "";

        if (user.getId() != null && userRepository.hasActiveGuideProfile(user.getId())) {
            throw new BranchConflictException(
                    "This user is currently an active Tour Guide (GUIDE) and cannot be assigned as branch staff!");
        }

        Optional<Staff> existingStaffOpt = staffRepository.findByUserId(request.getUserId());
        if (existingStaffOpt.isPresent() && StaffStatus.ACTIVE.name().equals(existingStaffOpt.get().getStatus())) {
            throw new BranchConflictException("User is already assigned as a staff member");
        }

        if (Roles.ADMIN.equals(currentRole)) {
            throw new BranchConflictException("Cannot assign an Administrator (ADMIN) account as branch staff!");
        }

        if (Roles.CUSTOMER.equals(currentRole) && UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new InvalidBranchOperationException("Only users with the STAFF role can be assigned to a branch!");
        }

        if (UserStatus.BLOCKED.name().equals(user.getStatus())) {
            throw new InvalidBranchOperationException("Cannot assign a blocked user account!");
        }

        if (!Roles.STAFF.equals(currentRole)) {
            if (roleRepository != null) {
                Role staffRole = roleRepository.findByName(Roles.STAFF).orElse(null);
                if (staffRole != null) {
                    user.setRole(staffRole);
                }
            } else {
                throw new InvalidBranchOperationException("Only users with the STAFF role can be assigned to a branch!");
            }
        }

        String status = StaffStatus.ACTIVE.name();
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            status = validateStaffStatus(request.getStatus());
        }

        if (StaffStatus.ACTIVE.name().equals(status) && !UserStatus.ACTIVE.name().equals(user.getStatus())) {
            user.setStatus(UserStatus.ACTIVE.name());
            userRepository.save(user);
        }

        Staff staff;
        if (existingStaffOpt.isPresent()) {
            staff = existingStaffOpt.get();
            staff.setBranch(branch);
            staff.setPosition(request.getPosition());
            staff.setStatus(status);
        } else {
            staff = Staff.builder()
                    .branch(branch)
                    .user(user)
                    .position(request.getPosition())
                    .status(status)
                    .build();
        }

        Staff savedStaff = staffRepository.save(staff);
        return mapToStaffResponse(savedStaff);
    }

    @Override
    public StaffResponse updateStaff(Long id, StaffRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new StaffNotFoundException("Staff not found with id: " + id));

        if (!staff.getBranch().getId().equals(request.getBranchId())) {
            Branch branch = branchRepository.findById(request.getBranchId())
                    .orElseThrow(
                            () -> new BranchNotFoundException("Branch not found with id: " + request.getBranchId()));
            if (branch.getStatus() != null && !BranchStatus.ACTIVE.name().equals(branch.getStatus())) {
                throw new InvalidBranchOperationException("Cannot transfer staff to an inactive branch!");
            }
            staff.setBranch(branch);
        }

        staff.setPosition(request.getPosition());

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String upperStatus = validateStaffStatus(request.getStatus());
            User user = staff.getUser();
            if (StaffStatus.ACTIVE.name().equals(upperStatus)) {
                if (user != null && user.getId() != null && userRepository.hasActiveGuideProfile(user.getId())) {
                    throw new BranchConflictException("This user is currently an active Tour Guide (GUIDE) and cannot be assigned as branch staff!");
                }
                if (user != null && !UserStatus.BLOCKED.name().equals(user.getStatus()) && !UserStatus.ACTIVE.name().equals(user.getStatus())) {
                    user.setStatus(UserStatus.ACTIVE.name());
                    userRepository.save(user);
                }
            } else if (StaffStatus.INACTIVE.name().equals(upperStatus)) {
                if (user != null && UserStatus.ACTIVE.name().equals(user.getStatus())
                        && (user.getId() == null || !userRepository.hasActiveGuideProfile(user.getId()))) {
                    user.setStatus(UserStatus.INACTIVE.name());
                    userRepository.save(user);
                }
            }
            staff.setStatus(upperStatus);
        }

        Staff updatedStaff = staffRepository.save(staff);
        return mapToStaffResponse(updatedStaff);
    }

    @Override
    public StaffResponse updateStaffStatus(Long id, String status) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new StaffNotFoundException("Staff not found with id: " + id));

        String upperStatus = validateStaffStatus(status);
        User user = staff.getUser();
        if (StaffStatus.ACTIVE.name().equals(upperStatus)) {
            Branch branch = staff.getBranch();
            if (branch.getStatus() != null && !BranchStatus.ACTIVE.name().equals(branch.getStatus())) {
                throw new InvalidBranchOperationException("Cannot activate staff member in an inactive branch!");
            }
            if (user != null && UserStatus.BLOCKED.name().equals(user.getStatus())) {
                throw new BranchConflictException("Cannot activate staff member with a blocked user account!");
            }
            if (user != null && user.getId() != null && userRepository.hasActiveGuideProfile(user.getId())) {
                throw new BranchConflictException("This user is currently an active Tour Guide (GUIDE) and cannot be activated as branch staff!");
            }
            if (user != null) {
                if (roleRepository != null && (user.getRole() == null || !Roles.STAFF.equals(user.getRole().getName()))) {
                    Role staffRole = roleRepository.findByName(Roles.STAFF).orElse(null);
                    if (staffRole != null) {
                        user.setRole(staffRole);
                    }
                }
                if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
                    user.setStatus(UserStatus.ACTIVE.name());
                    userRepository.save(user);
                }
            }
        } else if (StaffStatus.INACTIVE.name().equals(upperStatus)) {
            if (user != null && UserStatus.ACTIVE.name().equals(user.getStatus())
                    && (user.getId() == null || !userRepository.hasActiveGuideProfile(user.getId()))) {
                user.setStatus(UserStatus.INACTIVE.name());
                userRepository.save(user);
            }
        }
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
