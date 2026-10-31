package com.coc.sba_treektour.modules.account.dto.request;

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
public class AdminUpdateUserRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @Size(max = 30, message = "Phone number must not exceed 30 characters")
    private String phone;

    @Size(max = 50, message = "Role must not exceed 50 characters")
    private String role; // Optional: ADMIN, STAFF, GUIDE, CUSTOMER

    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status; // Optional: ACTIVE, INACTIVE, BLOCKED
}
