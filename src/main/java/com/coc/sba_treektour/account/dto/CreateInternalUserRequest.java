package com.coc.sba_treektour.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInternalUserRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    private String phone;

    @NotBlank(message = "Vai trò (Role) không được để trống")
    @Pattern(regexp = "^(STAFF|GUIDE)$", message = "Vai trò nội bộ chỉ có thể là STAFF hoặc GUIDE")
    private String role;
}
