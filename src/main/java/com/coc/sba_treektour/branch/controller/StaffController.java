package com.coc.sba_treektour.branch.controller;

import com.coc.sba_treektour.branch.dto.StaffRequest;
import com.coc.sba_treektour.branch.dto.StaffResponse;
import com.coc.sba_treektour.branch.service.StaffService;
import com.coc.sba_treektour.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> getStaffByBranch(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.success(staffService.getStaffByBranch(branchId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StaffResponse>> assignStaff(@Valid @RequestBody StaffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("Gán nhân viên thành công", staffService.assignStaff(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @PathVariable Long id, 
            @Valid @RequestBody StaffRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin nhân viên thành công", staffService.updateStaff(id, request)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaffStatus(
            @PathVariable Long id, 
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái nhân viên thành công", staffService.updateStaffStatus(id, status)));
    }
}
