package com.coc.sba_treektour.branch.service;

import com.coc.sba_treektour.branch.dto.StaffRequest;
import com.coc.sba_treektour.branch.dto.StaffResponse;
import com.coc.sba_treektour.branch.entity.Staff;

import java.util.List;

public interface StaffService {
    List<StaffResponse> getStaffByBranch(Long branchId);
    StaffResponse assignStaff(StaffRequest request);
    StaffResponse updateStaff(Long id, StaffRequest request);
    StaffResponse updateStaffStatus(Long id, String status);
    StaffResponse mapToStaffResponse(Staff staff);
}
