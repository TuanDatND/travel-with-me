package com.coc.sba_treektour.account;

import com.coc.sba_treektour.common.service.EmailService;
import com.coc.sba_treektour.modules.account.dto.request.AdminUpdateUserRequest;
import com.coc.sba_treektour.modules.account.dto.request.ChangePasswordRequest;
import com.coc.sba_treektour.modules.account.dto.response.UserResponse;
import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.exception.InvalidAccountException;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.account.service.UserService;
import com.coc.sba_treektour.modules.account.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
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

                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> userService.changePassword("test@example.com", request));
                assertTrue(ex.getMessage().contains("Current password is incorrect"));
        }

        @Test
        void testUpdateUserByAdmin_Success() {
                Role staffRole = Role.builder().id(2L).name("STAFF").build();
                Role adminRole = Role.builder().id(1L).name("ADMIN").build();
                User user = User.builder()
                                .id(10L)
                                .email("staff@example.com")
                                .fullName("Old")
                                .phone("0123")
                                .role(staffRole)
                                .status("ACTIVE")
                                .build();

                AdminUpdateUserRequest request = AdminUpdateUserRequest.builder()
                                .fullName("New")
                                .phone("0999")
                                .role("ADMIN")
                                .status("BLOCKED")
                                .build();

                when(userRepository.findById(10L)).thenReturn(Optional.of(user));
                when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
                when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

                UserResponse response = userService.updateUserByAdmin(10L, request);

                assertEquals("New", response.getFullName());
                assertEquals("0999", response.getPhone());
                assertEquals("ADMIN", response.getRole());
                assertEquals("BLOCKED", response.getStatus());
        }

        @Test
        void testUpdateUserByAdmin_SelfDemoteOrLock_ThrowsException() {
                Role adminRole = Role.builder().id(1L).name("ADMIN").build();
                User adminUser = User.builder()
                                .id(99L)
                                .email("admin@treektour.com")
                                .fullName("Admin")
                                .role(adminRole)
                                .status("ACTIVE")
                                .build();

                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(adminUser, null, adminUser.getAuthorities()));

                when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));

                AdminUpdateUserRequest demoteRequest = AdminUpdateUserRequest.builder()
                                .fullName("Admin")
                                .role("CUSTOMER")
                                .build();

                assertThrows(InvalidAccountException.class, () -> userService.updateUserByAdmin(99L, demoteRequest));
                assertThrows(InvalidAccountException.class, () -> userService.updateUserStatus(99L, "BLOCKED"));
                assertThrows(InvalidAccountException.class, () -> userService.deleteUser(99L));
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
    void testDeleteUser_WhenHasLinkedBusinessRecords_SoftDeletesToInactive() {
        User user = User.builder().id(10L).status("ACTIVE").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.hasActiveStaffProfile(10L)).thenReturn(false);
        when(userRepository.hasActiveGuideProfile(10L)).thenReturn(false);
        when(userRepository.hasLinkedBusinessRecords(10L)).thenReturn(true);

        boolean result = userService.deleteUser(10L);
        assertFalse(result);
        assertEquals("INACTIVE", user.getStatus());
        verify(userRepository).save(user);
    }

    @Test
    void testDeleteUser_WhenNoLinkedRecords_PermanentlyDeletes() {
        User user = User.builder().id(10L).status("ACTIVE").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.hasActiveStaffProfile(10L)).thenReturn(false);
        when(userRepository.hasActiveGuideProfile(10L)).thenReturn(false);
        when(userRepository.hasLinkedBusinessRecords(10L)).thenReturn(false);

        boolean result = userService.deleteUser(10L);
        assertTrue(result);
        verify(userRepository).delete(user);
        verify(userRepository).flush();
    }

    @Test
    void testDeleteUser_WhenActiveStaff_ThrowsInvalidAccountException() {
        User user = User.builder().id(10L).status("ACTIVE").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.hasActiveStaffProfile(10L)).thenReturn(true);

        assertThrows(InvalidAccountException.class, () -> userService.deleteUser(10L));
    }

    @Test
    void testDeleteUser_WhenActiveGuide_ThrowsInvalidAccountException() {
        User user = User.builder().id(10L).status("ACTIVE").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(userRepository.hasActiveStaffProfile(10L)).thenReturn(false);
        when(userRepository.hasActiveGuideProfile(10L)).thenReturn(true);

        assertThrows(InvalidAccountException.class, () -> userService.deleteUser(10L));
    }
}
