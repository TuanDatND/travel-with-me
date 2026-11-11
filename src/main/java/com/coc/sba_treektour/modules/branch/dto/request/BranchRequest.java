package com.coc.sba_treektour.modules.branch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchRequest {

    @NotBlank(message = "Branch name is required")
    @Size(max = 150, message = "Branch name must not exceed 150 characters")
    private String name;

    @Size(max = 30, message = "Phone number must not exceed 30 characters")
    private String phone;

    private String address;

    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;
}
