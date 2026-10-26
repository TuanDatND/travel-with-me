package com.coc.sba_treektour.modules.branch.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffResponse {
    private Long id;
    private Long branchId;
    private String branchName;
    private Long userId;
    private String userName;
    private String position;
    private String status;
}
