package com.coc.sba_treektour.account.service.impl;

import com.coc.sba_treektour.account.dto.CreateInternalUserRequest;
import com.coc.sba_treektour.account.dto.InternalUserResponse;
import com.coc.sba_treektour.account.dto.UserProfileRequest;
import com.coc.sba_treektour.account.dto.UserResponse;
import com.coc.sba_treektour.account.entity.Role;
import com.coc.sba_treektour.account.entity.User;
import com.coc.sba_treektour.account.repository.RoleRepository;
import com.coc.sba_treektour.account.repository.UserRepository;
import com.coc.sba_treektour.account.service.UserService;
import com.coc.sba_treektour.common.service.EmailService;
import com.coc.sba_treektour.common.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final @Lazy PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Override
    public InternalUserResponse createInternalUser(CreateInternalUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng trong hệ thống!");
        }

        String roleName = request.getRole().toUpperCase();
        if (!"STAFF".equals(roleName) && !"GUIDE".equals(roleName)) {
            throw new RuntimeException("Chỉ được phép tạo tài khoản nội bộ cho vai trò STAFF hoặc GUIDE!");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy vai trò: " + roleName));

        String rawPassword = PasswordGenerator.generateRandomPassword(10);

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .status("ACTIVE")
                .build();

        User savedUser = userRepository.save(user);

        boolean emailSent = emailService.sendInternalAccountEmail(
                savedUser.getEmail(),
                savedUser.getFullName(),
                role.getName(),
                rawPassword
        );

        String message = emailSent 
                ? "Tạo tài khoản thành công và đã gửi mật khẩu về email của nhân viên."
                : "Tạo tài khoản thành công nhưng gửi email thất bại (hãy kiểm tra cấu hình SMTP). Mật khẩu tạm thời được cung cấp bên dưới.";

        return InternalUserResponse.builder()
                .id(savedUser.getId())
                .fullName(savedUser.getFullName())
                .email(savedUser.getEmail())
                .phone(savedUser.getPhone())
                .role(role.getName())
                .status(savedUser.getStatus())
                .temporaryPassword(rawPassword)
                .emailSent(emailSent)
                .message(message)
                .build();
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse updateProfile(String email, UserProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        
        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    @Override
    public UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole().getName() : null)
                .status(user.getStatus())
                .build();
    }
}
