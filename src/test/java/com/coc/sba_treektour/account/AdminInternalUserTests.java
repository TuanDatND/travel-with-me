package com.coc.sba_treektour.account;

import com.coc.sba_treektour.account.dto.CreateInternalUserRequest;
import com.coc.sba_treektour.account.dto.InternalUserResponse;
import com.coc.sba_treektour.account.entity.Role;
import com.coc.sba_treektour.account.entity.User;
import com.coc.sba_treektour.account.repository.RoleRepository;
import com.coc.sba_treektour.account.repository.UserRepository;
import com.coc.sba_treektour.account.service.UserService;
import com.coc.sba_treektour.common.service.EmailService;
import com.coc.sba_treektour.common.util.PasswordGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import com.coc.sba_treektour.account.service.impl.UserServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AdminInternalUserTests {

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
    void testPasswordGenerator_ValidLengthAndComplexity() {
        String password = PasswordGenerator.generateRandomPassword(10);
        assertNotNull(password);
        assertEquals(10, password.length());
    }

    @Test
    void testCreateInternalUser_SuccessForStaff() {
        CreateInternalUserRequest request = CreateInternalUserRequest.builder()
                .fullName("Nguyễn Văn Nhân Viên")
                .email("staff.test@treektour.com")
                .phone("0987654321")
                .role("STAFF")
                .build();

        Role staffRole = Role.builder().id(2L).name("STAFF").build();

        when(userRepository.existsByEmail("staff.test@treektour.com")).thenReturn(false);
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(100L);
            return u;
        });
        when(emailService.sendInternalAccountEmail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        InternalUserResponse response = userService.createInternalUser(request);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("STAFF", response.getRole());
        assertTrue(response.isEmailSent());
        assertNotNull(response.getTemporaryPassword());
        assertEquals(10, response.getTemporaryPassword().length());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("staff.test@treektour.com", saved.getEmail());
        assertEquals("STAFF", saved.getRole().getName());
        assertEquals("hashed_password", saved.getPassword());
    }

    @Test
    void testCreateInternalUser_RejectCustomerRole() {
        CreateInternalUserRequest request = CreateInternalUserRequest.builder()
                .fullName("Test Customer")
                .email("customer@treektour.com")
                .role("CUSTOMER")
                .build();

        when(userRepository.existsByEmail("customer@treektour.com")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.createInternalUser(request));
        assertTrue(ex.getMessage().contains("Chỉ được phép tạo tài khoản nội bộ cho vai trò STAFF hoặc GUIDE"));
    }
}
