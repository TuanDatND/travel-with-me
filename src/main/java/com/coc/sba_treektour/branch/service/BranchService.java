package com.coc.sba_treektour.branch.service;

import com.coc.sba_treektour.branch.dto.BranchRequest;
import com.coc.sba_treektour.branch.dto.BranchResponse;
import com.coc.sba_treektour.branch.entity.Branch;

import java.util.List;

public interface BranchService {
    List<BranchResponse> getAllBranches();
    BranchResponse getBranchById(Long id);
    BranchResponse createBranch(BranchRequest request);
    BranchResponse updateBranch(Long id, BranchRequest request);
    BranchResponse updateBranchStatus(Long id, String status);
    BranchResponse mapToBranchResponse(Branch branch);
}
