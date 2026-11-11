package com.coc.sba_treektour.modules.branch.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @Size(max = 100, message = "Position must not exceed 100 characters")
    private String position;

    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;
}
