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

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 150, message = "Họ và tên không được vượt quá 150 ký tự")
    private String fullName;

    @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
    private String phone;

    @Size(max = 50, message = "Vai trò không được vượt quá 50 ký tự")
    private String role; // Optional: ADMIN, STAFF, GUIDE, CUSTOMER

    @Size(max = 30, message = "Trạng thái không được vượt quá 30 ký tự")
    private String status; // Optional: ACTIVE, INACTIVE, BLOCKED
}
