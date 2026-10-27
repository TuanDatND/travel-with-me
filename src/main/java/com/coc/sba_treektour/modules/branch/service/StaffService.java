package com.coc.sba_treektour.modules.branch.service;

import com.coc.sba_treektour.modules.branch.dto.request.StaffRequest;
import com.coc.sba_treektour.modules.branch.dto.response.StaffResponse;
import com.coc.sba_treektour.modules.branch.entity.Staff;

import java.util.List;

public interface StaffService {
    List<StaffResponse> getAllStaff();
    List<StaffResponse> getStaffByBranch(Long branchId);
    StaffResponse assignStaff(StaffRequest request);
    StaffResponse updateStaff(Long id, StaffRequest request);
    StaffResponse updateStaffStatus(Long id, String status);
    void deleteStaff(Long id);
    StaffResponse mapToStaffResponse(Staff staff);
}
