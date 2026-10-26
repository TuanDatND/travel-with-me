package com.coc.sba_treektour.modules.branch.service;

import com.coc.sba_treektour.modules.branch.dto.request.BranchRequest;
import com.coc.sba_treektour.modules.branch.dto.response.BranchResponse;
import com.coc.sba_treektour.modules.branch.entity.Branch;

import java.util.List;

public interface BranchService {
    List<BranchResponse> getAllBranches();
    BranchResponse getBranchById(Long id);
    BranchResponse createBranch(BranchRequest request);
    BranchResponse updateBranch(Long id, BranchRequest request);
    BranchResponse updateBranchStatus(Long id, String status);
    void deleteBranch(Long id);
    BranchResponse mapToBranchResponse(Branch branch);
}
