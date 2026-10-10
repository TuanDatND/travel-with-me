package com.coc.sba_treektour.branch.controller;

import com.coc.sba_treektour.branch.dto.BranchRequest;
import com.coc.sba_treektour.branch.dto.BranchResponse;
import com.coc.sba_treektour.branch.service.BranchService;
import com.coc.sba_treektour.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getAllBranches() {
        return ResponseEntity.ok(ApiResponse.success(branchService.getAllBranches()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranchById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(branchService.getBranchById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BranchResponse>> createBranch(@Valid @RequestBody BranchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Tạo chi nhánh thành công", branchService.createBranch(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranch(
            @PathVariable Long id, 
            @Valid @RequestBody BranchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật chi nhánh thành công", branchService.updateBranch(id, request)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BranchResponse>> updateBranchStatus(
            @PathVariable Long id, 
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công", branchService.updateBranchStatus(id, status)));
    }
}
