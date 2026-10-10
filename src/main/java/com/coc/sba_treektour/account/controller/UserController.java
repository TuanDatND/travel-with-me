package com.coc.sba_treektour.account.controller;

import com.coc.sba_treektour.account.dto.UserProfileRequest;
import com.coc.sba_treektour.account.dto.UserResponse;
import com.coc.sba_treektour.account.service.UserService;
import com.coc.sba_treektour.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers()));
    }

    @GetMapping("/{id}")
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
}
