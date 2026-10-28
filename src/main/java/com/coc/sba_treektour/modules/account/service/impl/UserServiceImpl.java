package com.coc.sba_treektour.modules.account.service.impl;

import com.coc.sba_treektour.common.security.Roles;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
        String normalizedEmail = normalizeEmail(request.getEmail());

        String roleName = request.getRole().trim().toUpperCase(Locale.ROOT);
        if (!Roles.STAFF.equals(roleName) && !Roles.GUIDE.equals(roleName)) {
            throw new InvalidAccountException("Chỉ được phép tạo tài khoản nội bộ cho vai trò STAFF hoặc GUIDE!");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new InvalidAccountException("Không tìm thấy vai trò: " + roleName));

        String rawPassword = PasswordGenerator.generateRandomPassword(10);

        Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);
        User user;

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            String existingRole = existingUser.getRole() != null ? existingUser.getRole().getName() : "";

            if (Roles.ADMIN.equals(existingRole)) {
                throw new InvalidAccountException("Email này đang thuộc về tài khoản Quản trị viên (ADMIN)!");
            }

            if (Roles.CUSTOMER.equals(existingRole) && UserStatus.ACTIVE.name().equals(existingUser.getStatus())) {
                throw new InvalidAccountException("Email này đang thuộc về tài khoản Khách hàng (CUSTOMER) đang hoạt động!");
            }

            Long existingUserId = existingUser.getId();
            if (existingUserId != null) {
                boolean activeGuide = userRepository.hasActiveGuideProfile(existingUserId);
                boolean activeStaff = userRepository.hasActiveStaffProfile(existingUserId);

                if (Roles.STAFF.equals(roleName)) {
                    if (activeGuide) {
                        throw new InvalidAccountException(
                                "Email này đang là Hướng dẫn viên (GUIDE) đang hoạt động! Hãy khóa/ngừng hoạt động Hướng dẫn viên trước khi cấp làm STAFF."
                        );
                    }
                    if (activeStaff) {
                        throw new InvalidAccountException(
                                "Email này đang được phân công làm Nhân viên chi nhánh (STAFF) đang hoạt động!"
                        );
                    }
                } else if (Roles.GUIDE.equals(roleName)) {
                    if (activeStaff) {
                        throw new InvalidAccountException(
                                "Email này đang là Nhân viên chi nhánh (STAFF) đang hoạt động! Hãy xóa/khóa nhân viên chi nhánh trước khi cấp làm GUIDE."
                        );
                    }
                    if (activeGuide) {
                        throw new InvalidAccountException(
                                "Email này đang là Hướng dẫn viên (GUIDE) đang hoạt động trong hệ thống!"
                        );
                    }
                    if (userRepository.hasAnyGuideProfile(existingUserId)) {
                        throw new InvalidAccountException(
                                "Email này đã có hồ sơ Hướng dẫn viên (đang bị khóa). Vui lòng mở khóa trực tiếp trên danh sách Hướng dẫn viên thay vì tạo mới!"
                        );
                    }
                }
            }

            existingUser.setFullName(request.getFullName().trim());
            existingUser.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
            existingUser.setPassword(passwordEncoder.encode(rawPassword));
            existingUser.setRole(role);
            existingUser.setStatus(UserStatus.ACTIVE.name());
            user = existingUser;
        } else {
            user = User.builder()
                    .fullName(request.getFullName().trim())
                    .email(normalizedEmail)
                    .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(role)
                    .status(UserStatus.ACTIVE.name())
                    .build();
        }

        User savedUser = userRepository.save(user);
        userRepository.flush();

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
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + normalizedEmail));
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse updateProfile(String email, UserProfileRequest request) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + normalizedEmail));

        user.setFullName(request.getFullName().trim());
        user.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);

        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    @Override
    public void changePassword(String email, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidAccountException("Mật khẩu mới và xác nhận mật khẩu không trùng khớp!");
        }

        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + normalizedEmail));

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

        boolean isSelf = isCurrentAuthenticatedUser(user);

        user.setFullName(request.getFullName().trim());
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }

        if (request.getRole() != null && !request.getRole().isBlank()) {
            String upperRole = request.getRole().trim().toUpperCase(Locale.ROOT);
            if (isSelf && !Roles.ADMIN.equals(upperRole)) {
                throw new InvalidAccountException("Quản trị viên không thể tự hạ quyền của chính mình!");
            }
            Role role = roleRepository.findByName(upperRole)
                    .orElseThrow(() -> new InvalidAccountException("Không tìm thấy vai trò: " + request.getRole()));
            user.setRole(role);
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase(Locale.ROOT);
            try {
                UserStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new InvalidAccountException("Trạng thái không hợp lệ. Phải là ACTIVE, INACTIVE hoặc BLOCKED");
            }
            if (isSelf && !UserStatus.ACTIVE.name().equals(status)) {
                throw new InvalidAccountException("Quản trị viên không thể tự khóa hoặc vô hiệu hóa tài khoản của chính mình!");
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

        String upperStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        try {
            UserStatus.valueOf(upperStatus);
        } catch (IllegalArgumentException e) {
            throw new InvalidAccountException("Trạng thái không hợp lệ. Phải là ACTIVE, INACTIVE hoặc BLOCKED");
        }

        if (isCurrentAuthenticatedUser(user) && !UserStatus.ACTIVE.name().equals(upperStatus)) {
            throw new InvalidAccountException("Quản trị viên không thể tự khóa hoặc vô hiệu hóa tài khoản của chính mình!");
        }

        user.setStatus(upperStatus);
        User updatedUser = userRepository.save(user);
        return mapToUserResponse(updatedUser);
    }

    @Override
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if (isCurrentAuthenticatedUser(user)) {
            throw new InvalidAccountException("Quản trị viên không thể tự xóa tài khoản của chính mình!");
        }

        user.setStatus(UserStatus.INACTIVE.name());
        userRepository.save(user);
    }

    private boolean isCurrentAuthenticatedUser(User targetUser) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        if (auth.getPrincipal() instanceof User currentUser && currentUser.getId() != null) {
            return currentUser.getId().equals(targetUser.getId());
        }
        return targetUser.getEmail() != null && targetUser.getEmail().equalsIgnoreCase(auth.getName());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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
