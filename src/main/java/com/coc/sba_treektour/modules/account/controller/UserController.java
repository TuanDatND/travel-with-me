package com.coc.sba_treektour.modules.account.controller;

import com.coc.sba_treektour.common.response.ApiResponse;
import com.coc.sba_treektour.modules.account.dto.request.AdminUpdateUserRequest;
import com.coc.sba_treektour.modules.account.dto.request.ChangePasswordRequest;
import com.coc.sba_treektour.modules.account.dto.request.CreateInternalUserRequest;
import com.coc.sba_treektour.modules.account.dto.request.UserProfileRequest;
import com.coc.sba_treektour.modules.account.dto.response.InternalUserResponse;
import com.coc.sba_treektour.modules.account.dto.response.UserResponse;
import com.coc.sba_treektour.modules.account.exception.InvalidAccountException;
import com.coc.sba_treektour.modules.account.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
            throw new InvalidAccountException("Chưa đăng nhập");
        }
        return ResponseEntity.ok(ApiResponse.success(userService.getUserByEmail(principal.getName())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            Principal principal, 
            @Valid @RequestBody UserProfileRequest request) {
        if (principal == null) {
            throw new InvalidAccountException("Chưa đăng nhập");
        }
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công", userService.updateProfile(principal.getName(), request)));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Principal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        if (principal == null) {
            throw new InvalidAccountException("Chưa đăng nhập");
        }
        userService.changePassword(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công!", null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserByAdmin(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
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
