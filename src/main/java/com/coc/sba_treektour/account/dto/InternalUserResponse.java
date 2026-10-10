package com.coc.sba_treektour.account.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalUserResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String role;
    private String status;
    private String temporaryPassword;
    private boolean emailSent;
    private String message;
}
