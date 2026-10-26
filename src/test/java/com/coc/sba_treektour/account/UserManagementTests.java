package com.coc.sba_treektour.account;

import com.coc.sba_treektour.modules.account.dto.request.AdminUpdateUserRequest;
import com.coc.sba_treektour.modules.account.dto.request.ChangePasswordRequest;
import com.coc.sba_treektour.modules.account.dto.response.UserResponse;
import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.account.service.UserService;
import com.coc.sba_treektour.modules.account.service.impl.UserServiceImpl;
import com.coc.sba_treektour.common.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class UserManagementTests {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        userService = new UserServiceImpl(userRepository, roleRepository, passwordEncoder, emailService);
    }

    @Test
    void testChangePassword_Success() {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("hashed_old_pass")
                .role(role)
                .build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("old_pass")
                .newPassword("new_pass_123")
                .confirmPassword("new_pass_123")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old_pass", "hashed_old_pass")).thenReturn(true);
        when(passwordEncoder.encode("new_pass_123")).thenReturn("hashed_new_pass");

        userService.changePassword("test@example.com", request);

        assertEquals("hashed_new_pass", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void testChangePassword_WrongOldPassword() {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("hashed_old_pass")
                .role(role)
                .build();

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrong_pass")
                .newPassword("new_pass_123")
                .confirmPassword("new_pass_123")
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_pass", "hashed_old_pass")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                userService.changePassword("test@example.com", request));
        assertTrue(ex.getMessage().contains("Mật khẩu hiện tại không chính xác"));
    }

    @Test
    void testUpdateUserByAdmin_Success() {
        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        Role adminRole = Role.builder().id(1L).name("ADMIN").build();
        User user = User.builder()
                .id(10L)
                .email("staff@example.com")
                .fullName("Cũ")
                .phone("0123")
                .role(staffRole)
                .status("ACTIVE")
                .build();

        AdminUpdateUserRequest request = AdminUpdateUserRequest.builder()
                .fullName("Mới")
                .phone("0999")
                .role("ADMIN")
                .status("BLOCKED")
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserByAdmin(10L, request);

        assertEquals("Mới", response.getFullName());
        assertEquals("0999", response.getPhone());
        assertEquals("ADMIN", response.getRole());
        assertEquals("BLOCKED", response.getStatus());
    }

    @Test
    void testUpdateUserStatus_Success() {
        Role role = Role.builder().id(1L).name("STAFF").build();
        User user = User.builder().id(10L).role(role).status("ACTIVE").build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUserStatus(10L, "BLOCKED");
        assertEquals("BLOCKED", response.getStatus());
    }

    @Test
    void testDeleteUser_Success() {
        User user = User.builder().id(10L).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        userService.deleteUser(10L);
        verify(userRepository).delete(user);
    }
}
