package com.coc.sba_treektour.branch.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffRequest {

    @NotNull(message = "Branch ID is required")
    private Long branchId;

    @NotNull(message = "User ID is required")
    private Long userId;

    private String position;
    private String status;
}
