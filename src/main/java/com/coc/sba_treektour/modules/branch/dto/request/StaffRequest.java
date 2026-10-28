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

    @NotNull(message = "Branch ID không được để trống")
    private Long branchId;

    @NotNull(message = "User ID không được để trống")
    private Long userId;

    @Size(max = 100, message = "Chức vụ không được vượt quá 100 ký tự")
    private String position;

    @Size(max = 30, message = "Trạng thái không được vượt quá 30 ký tự")
    private String status;
}
