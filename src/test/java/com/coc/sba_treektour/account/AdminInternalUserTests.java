package com.coc.sba_treektour.account;

import com.coc.sba_treektour.common.service.EmailService;
import com.coc.sba_treektour.common.util.PasswordGenerator;
import com.coc.sba_treektour.modules.account.dto.request.CreateInternalUserRequest;
import com.coc.sba_treektour.modules.account.dto.response.InternalUserResponse;
import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.exception.InvalidAccountException;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.account.service.UserService;
import com.coc.sba_treektour.modules.account.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                .fullName("John Staff")
                .email("  Staff.Test@TreekTour.com  ")
                .phone("0987654321")
                .role("STAFF")
                .build();

        Role staffRole = Role.builder().id(2L).name("STAFF").build();

        when(userRepository.findByEmail("staff.test@treektour.com")).thenReturn(Optional.empty());
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
    void testCreateInternalUser_ReassignExistingInactiveOrUnassignedUser() {
        CreateInternalUserRequest request = CreateInternalUserRequest.builder()
                .fullName("Dat Tran")
                .email("datgalaxy0704@gmail.com")
                .phone("0912345678")
                .role("STAFF")
                .build();

        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        User existingUser = User.builder()
                .id(55L)
                .fullName("Old Name")
                .email("datgalaxy0704@gmail.com")
                .role(staffRole)
                .status("INACTIVE")
                .build();

        when(userRepository.findByEmail("datgalaxy0704@gmail.com")).thenReturn(Optional.of(existingUser));
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode(anyString())).thenReturn("new_hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.sendInternalAccountEmail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        InternalUserResponse response = userService.createInternalUser(request);

        assertNotNull(response);
        assertEquals(55L, response.getId());
        assertEquals("Dat Tran", response.getFullName());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void testCreateInternalUser_ActiveGuideCannotBecomeStaff_ThrowsException() {
        CreateInternalUserRequest request = CreateInternalUserRequest.builder()
                .fullName("Active Guide")
                .email("guide.active@treektour.com")
                .phone("0912345678")
                .role("STAFF")
                .build();

        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        Role guideRole = Role.builder().id(3L).name("GUIDE").build();
        User activeGuideUser = User.builder()
                .id(77L)
                .fullName("Active Guide")
                .email("guide.active@treektour.com")
                .role(guideRole)
                .status("ACTIVE")
                .build();

        when(userRepository.findByEmail("guide.active@treektour.com")).thenReturn(Optional.of(activeGuideUser));
        when(userRepository.hasActiveGuideProfile(77L)).thenReturn(true);
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));

        InvalidAccountException ex = assertThrows(InvalidAccountException.class,
                () -> userService.createInternalUser(request));
        assertTrue(ex.getMessage().contains("active Tour Guide (GUIDE)"));

        // Even if the user's role in users table was previously changed to STAFF, active tour_guides still blocks it
        activeGuideUser.setRole(staffRole);
        InvalidAccountException ex2 = assertThrows(InvalidAccountException.class,
                () -> userService.createInternalUser(request));
        assertTrue(ex2.getMessage().contains("active Tour Guide (GUIDE)"));
    }

    @Test
    void testCreateInternalUser_LockedGuideCanBecomeStaff_Success() {
        CreateInternalUserRequest request = CreateInternalUserRequest.builder()
                .fullName("Locked Guide")
                .email("guide.locked@treektour.com")
                .phone("0912345678")
                .role("STAFF")
                .build();

        Role staffRole = Role.builder().id(2L).name("STAFF").build();
        Role guideRole = Role.builder().id(3L).name("GUIDE").build();
        User lockedGuideUser = User.builder()
                .id(78L)
                .fullName("Locked Guide")
                .email("guide.locked@treektour.com")
                .role(guideRole)
                .status("ACTIVE") // User table still ACTIVE, but tour_guides table is INACTIVE (locked on UI)
                .build();

        when(userRepository.findByEmail("guide.locked@treektour.com")).thenReturn(Optional.of(lockedGuideUser));
        when(userRepository.hasActiveGuideProfile(78L)).thenReturn(false);
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode(anyString())).thenReturn("new_hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(emailService.sendInternalAccountEmail(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        InternalUserResponse response = userService.createInternalUser(request);

        assertNotNull(response);
        assertEquals(78L, response.getId());
        assertEquals("STAFF", response.getRole());
        assertEquals("ACTIVE", response.getStatus());

        // Creating another GUIDE profile for a user who already has a guide profile (even if locked) is blocked before sending email
        CreateInternalUserRequest guideRequest = CreateInternalUserRequest.builder()
                .fullName("Locked Guide")
                .email("guide.locked@treektour.com")
                .phone("0912345678")
                .role("GUIDE")
                .build();
        when(roleRepository.findByName("GUIDE")).thenReturn(Optional.of(guideRole));
        when(userRepository.hasAnyGuideProfile(78L)).thenReturn(true);

        InvalidAccountException exGuide = assertThrows(InvalidAccountException.class,
                () -> userService.createInternalUser(guideRequest));
        assertTrue(exGuide.getMessage().contains("already has a Tour Guide profile"));
    }

    @Test
    void testCreateInternalUser_RejectCustomerRole() {
        CreateInternalUserRequest request = CreateInternalUserRequest.builder()
                .fullName("Test Customer")
                .email("customer@treektour.com")
                .role("CUSTOMER")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> userService.createInternalUser(request));
        assertTrue(ex.getMessage().contains("Internal accounts can only be created for STAFF or GUIDE roles"));
    }
}
