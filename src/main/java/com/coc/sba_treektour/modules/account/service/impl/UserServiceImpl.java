package com.coc.sba_treektour.modules.account.service.impl;

import com.coc.sba_treektour.common.service.EmailService;
import com.coc.sba_treektour.common.util.PasswordGenerator;
import com.coc.sba_treektour.modules.account.dto.request.AdminUpdateUserRequest;
import com.coc.sba_treektour.modules.account.dto.request.ChangePasswordRequest;
import com.coc.sba_treektour.modules.account.dto.request.CreateInternalUserRequest;
import com.coc.sba_treektour.modules.account.dto.request.UserProfileRequest;
import com.coc.sba_treektour.modules.account.dto.response.InternalUserResponse;
import com.coc.sba_treektour.modules.account.dto.response.UserResponse;
import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.enums.UserStatus;
import com.coc.sba_treektour.modules.account.exception.InvalidAccountException;
import com.coc.sba_treektour.modules.account.exception.UserNotFoundException;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.account.service.UserService;
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
            throw new InvalidAccountException("Email đã được sử dụng trong hệ thống!");
        }

        String roleName = request.getRole().toUpperCase();
        if (!"STAFF".equals(roleName) && !"GUIDE".equals(roleName)) {
            throw new InvalidAccountException("Chỉ được phép tạo tài khoản nội bộ cho vai trò STAFF hoặc GUIDE!");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new InvalidAccountException("Không tìm thấy vai trò: " + roleName));

        String rawPassword = PasswordGenerator.generateRandomPassword(10);

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .status(UserStatus.ACTIVE.name())
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
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse updateProfile(String email, UserProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        
        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    @Override
    public void changePassword(String email, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidAccountException("Mật khẩu mới và xác nhận mật khẩu không trùng khớp!");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidAccountException("Mật khẩu hiện tại không chính xác!");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public UserResponse updateUserByAdmin(Long id, AdminUpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        user.setFullName(request.getFullName());
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        if (request.getRole() != null && !request.getRole().isBlank()) {
            Role role = roleRepository.findByName(request.getRole().toUpperCase())
                    .orElseThrow(() -> new InvalidAccountException("Không tìm thấy vai trò: " + request.getRole()));
            user.setRole(role);
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().toUpperCase();
            try {
                UserStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new InvalidAccountException("Trạng thái không hợp lệ. Phải là ACTIVE, INACTIVE hoặc BLOCKED");
            }
            user.setStatus(status);
        }

        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    @Override
    public UserResponse updateUserStatus(Long id, String status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        String upperStatus = status.toUpperCase();
        try {
            UserStatus.valueOf(upperStatus);
        } catch (IllegalArgumentException e) {
            throw new InvalidAccountException("Trạng thái không hợp lệ. Phải là ACTIVE, INACTIVE hoặc BLOCKED");
        }

        user.setStatus(upperStatus);
        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    @Override
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        try {
            userRepository.delete(user);
        } catch (Exception ex) {
            user.setStatus(UserStatus.INACTIVE.name());
            userRepository.save(user);
        }
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
