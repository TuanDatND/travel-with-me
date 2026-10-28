package com.coc.sba_treektour.modules.branch.service.impl;

import com.coc.sba_treektour.modules.branch.dto.request.BranchRequest;
import com.coc.sba_treektour.modules.branch.dto.response.BranchResponse;
import com.coc.sba_treektour.modules.branch.entity.Branch;
import com.coc.sba_treektour.modules.branch.enums.BranchStatus;
import com.coc.sba_treektour.modules.branch.exception.BranchNotFoundException;
import com.coc.sba_treektour.modules.branch.exception.InvalidBranchOperationException;
import com.coc.sba_treektour.modules.branch.repository.BranchRepository;
import com.coc.sba_treektour.modules.branch.repository.StaffRepository;
import com.coc.sba_treektour.modules.branch.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponse> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(this::mapToBranchResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponse getBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Branch not found with id: " + id));
        return mapToBranchResponse(branch);
    }

    @Override
    public BranchResponse createBranch(BranchRequest request) {
        String status = BranchStatus.ACTIVE.name();
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            status = validateBranchStatus(request.getStatus());
        }

        Branch branch = Branch.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .status(status)
                .build();

        Branch savedBranch = branchRepository.save(branch);
        return mapToBranchResponse(savedBranch);
    }

    @Override
    public BranchResponse updateBranch(Long id, BranchRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Branch not found with id: " + id));

        branch.setName(request.getName());
        branch.setPhone(request.getPhone());
        branch.setAddress(request.getAddress());

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            branch.setStatus(validateBranchStatus(request.getStatus()));
        }

        Branch updatedBranch = branchRepository.save(branch);
        return mapToBranchResponse(updatedBranch);
    }

    @Override
    public BranchResponse updateBranchStatus(Long id, String status) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Branch not found with id: " + id));

        String upperStatus = validateBranchStatus(status);
        branch.setStatus(upperStatus);
        Branch updatedBranch = branchRepository.save(branch);
        return mapToBranchResponse(updatedBranch);
    }

    @Override
    public void deleteBranch(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("Branch not found with id: " + id));

        if (staffRepository.existsByBranchId(id)) {
            throw new InvalidBranchOperationException("Không thể xóa chi nhánh vì vẫn còn nhân viên đang trực thuộc!");
        }

        branch.setStatus(BranchStatus.INACTIVE.name());
        branchRepository.save(branch);
    }

    private String validateBranchStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new InvalidBranchOperationException("Invalid status. Must be ACTIVE or INACTIVE");
        }
        String upperStatus = status.toUpperCase();
        try {
            BranchStatus.valueOf(upperStatus);
            return upperStatus;
        } catch (IllegalArgumentException e) {
            throw new InvalidBranchOperationException("Invalid status. Must be ACTIVE or INACTIVE");
        }
    }

    @Override
    public BranchResponse mapToBranchResponse(Branch branch) {
        return BranchResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .phone(branch.getPhone())
                .address(branch.getAddress())
                .status(branch.getStatus())
                .build();
    }
}
