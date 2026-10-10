package com.coc.sba_treektour.branch.service;

import com.coc.sba_treektour.branch.dto.BranchRequest;
import com.coc.sba_treektour.branch.dto.BranchResponse;
import com.coc.sba_treektour.branch.entity.Branch;
import com.coc.sba_treektour.branch.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BranchService {

    private final BranchRepository branchRepository;

    public List<BranchResponse> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(this::mapToBranchResponse)
                .collect(Collectors.toList());
    }

    public BranchResponse getBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + id));
        return mapToBranchResponse(branch);
    }

    public BranchResponse createBranch(BranchRequest request) {
        Branch branch = Branch.builder()
                .name(request.getName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .status("ACTIVE")
                .build();
        
        Branch savedBranch = branchRepository.save(branch);
        return mapToBranchResponse(savedBranch);
    }

    public BranchResponse updateBranch(Long id, BranchRequest request) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + id));

        branch.setName(request.getName());
        branch.setPhone(request.getPhone());
        branch.setAddress(request.getAddress());

        Branch updatedBranch = branchRepository.save(branch);
        return mapToBranchResponse(updatedBranch);
    }

    public BranchResponse updateBranchStatus(Long id, String status) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + id));
        
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new RuntimeException("Invalid status. Must be ACTIVE or INACTIVE");
        }

        branch.setStatus(status);
        Branch updatedBranch = branchRepository.save(branch);
        return mapToBranchResponse(updatedBranch);
    }

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
