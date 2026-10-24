package com.coc.sba_treektour.modules.account.controller;

import com.coc.sba_treektour.modules.account.dto.CreateInternalUserRequest;
import com.coc.sba_treektour.modules.account.dto.InternalUserResponse;
import com.coc.sba_treektour.modules.account.dto.UserProfileRequest;
import com.coc.sba_treektour.modules.account.dto.UserResponse;
import com.coc.sba_treektour.modules.account.service.UserService;
import com.coc.sba_treektour.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/internal")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<InternalUserResponse>> createInternalUser(
            @Valid @RequestBody CreateInternalUserRequest request) {
        InternalUserResponse response = userService.createInternalUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response.getMessage(), response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByEmail(principal.getName())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            Principal principal, 
            @Valid @RequestBody UserProfileRequest request) {
        if (principal == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công", userService.updateProfile(principal.getName(), request)));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Principal principal,
            @Valid @RequestBody com.coc.sba_treektour.modules.account.dto.ChangePasswordRequest request) {
        if (principal == null) {
            throw new RuntimeException("Chưa đăng nhập");
        }
        userService.changePassword(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công!", null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserByAdmin(
            @PathVariable Long id,
            @Valid @RequestBody com.coc.sba_treektour.modules.account.dto.AdminUpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin người dùng thành công", userService.updateUserByAdmin(id, request)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái người dùng thành công", userService.updateUserStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa người dùng thành công", null));
    }
}
