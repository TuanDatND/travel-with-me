package com.coc.sba_treektour.branch.service;

import com.coc.sba_treektour.account.entity.User;
import com.coc.sba_treektour.account.repository.UserRepository;
import com.coc.sba_treektour.branch.dto.StaffRequest;
import com.coc.sba_treektour.branch.dto.StaffResponse;
import com.coc.sba_treektour.branch.entity.Branch;
import com.coc.sba_treektour.branch.entity.Staff;
import com.coc.sba_treektour.branch.repository.BranchRepository;
import com.coc.sba_treektour.branch.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    public List<StaffResponse> getStaffByBranch(Long branchId) {
        return staffRepository.findByBranchId(branchId).stream()
                .map(this::mapToStaffResponse)
                .collect(Collectors.toList());
    }

    public StaffResponse assignStaff(StaffRequest request) {
        if (staffRepository.existsByUserId(request.getUserId())) {
            throw new RuntimeException("User is already assigned as a staff member");
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + request.getBranchId()));
        
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + request.getUserId()));

        Staff staff = Staff.builder()
                .branch(branch)
                .user(user)
                .position(request.getPosition())
                .status("ACTIVE")
                .build();
        
        Staff savedStaff = staffRepository.save(staff);
        return mapToStaffResponse(savedStaff);
    }

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
