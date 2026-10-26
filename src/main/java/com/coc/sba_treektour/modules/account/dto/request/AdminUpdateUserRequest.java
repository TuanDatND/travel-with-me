package com.coc.sba_treektour.modules.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUpdateUserRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    private String phone;

    private String role; // Optional: ADMIN, STAFF, GUIDE, CUSTOMER

    private String status; // Optional: ACTIVE, INACTIVE, BLOCKED
}
