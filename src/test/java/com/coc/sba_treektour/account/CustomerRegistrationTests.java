package com.coc.sba_treektour.account;

import com.coc.sba_treektour.common.config.JwtService;
import com.coc.sba_treektour.common.security.Roles;
import com.coc.sba_treektour.common.service.EmailService;
import com.coc.sba_treektour.modules.account.controller.OAuth2AuthenticationSuccessHandler;
import com.coc.sba_treektour.modules.account.dto.request.LoginRequest;
import com.coc.sba_treektour.modules.account.dto.request.RegisterRequest;
import com.coc.sba_treektour.modules.account.dto.response.AuthResponse;
import com.coc.sba_treektour.modules.account.dto.response.UserResponse;
import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.enums.UserStatus;
import com.coc.sba_treektour.modules.account.exception.InvalidAccountException;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.account.service.AuthService;
import com.coc.sba_treektour.modules.account.service.UserService;
import com.coc.sba_treektour.modules.account.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CustomerRegistrationTests {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private UserService userService;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthenticationManager authenticationManager;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        userService = mock(UserService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        authenticationManager = mock(AuthenticationManager.class);

        authService = new AuthServiceImpl(
                userRepository,
                roleRepository,
                userService,
                passwordEncoder,
                jwtService,
                authenticationManager
        );
    }

    @Test
    void testRegisterCustomer_NewEmail_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("  New Customer  ")
                .email("  KhachHang.Moi@Example.com  ")
                .phone("   ")
                .password("secret123")
                .build();

        Role customerRole = Role.builder().id(4L).name(Roles.CUSTOMER).build();
        when(roleRepository.findByName(Roles.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(userRepository.findByEmail("khachhang.moi@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret123")).thenReturn("hashed_secret123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(201L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt_customer_token");
        when(userService.mapToUserResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return UserResponse.builder()
                    .id(u.getId())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .role(u.getRole().getName())
                    .status(u.getStatus())
                    .build();
        });

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt_customer_token", response.getToken());
        assertNotNull(response.getUser());
        assertEquals(201L, response.getUser().getId());
        assertEquals("New Customer", response.getUser().getFullName());
        assertEquals("khachhang.moi@example.com", response.getUser().getEmail());
        assertNull(response.getUser().getPhone());
        assertEquals(Roles.CUSTOMER, response.getUser().getRole());
        assertEquals(UserStatus.ACTIVE.name(), response.getUser().getStatus());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed_secret123", captor.getValue().getPassword());
    }

    @Test
    void testRegisterCustomer_ActiveOrBlockedEmail_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Duplicate Customer")
                .email("duplicate@example.com")
                .password("secret123")
                .build();

        Role customerRole = Role.builder().id(4L).name(Roles.CUSTOMER).build();
        User activeUser = User.builder()
                .id(10L)
                .email("duplicate@example.com")
                .role(customerRole)
                .status(UserStatus.ACTIVE.name())
                .build();

        when(roleRepository.findByName(Roles.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(userRepository.findByEmail("duplicate@example.com")).thenReturn(Optional.of(activeUser));

        InvalidAccountException ex = assertThrows(InvalidAccountException.class,
                () -> authService.register(request));
        assertTrue(ex.getMessage().contains("Email is already in use in the system"));
    }

    @Test
    void testRegisterCustomer_ReactivatesInactiveDeletedAccount_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .fullName("Reactivated Customer")
                .email("deleted.user@example.com")
                .phone("0912345678")
                .password("newpass123")
                .build();

        Role customerRole = Role.builder().id(4L).name(Roles.CUSTOMER).build();
        Role staffRole = Role.builder().id(2L).name(Roles.STAFF).build();
        User inactiveUser = User.builder()
                .id(87L)
                .fullName("Old Name")
                .email("deleted.user@example.com")
                .role(staffRole)
                .status(UserStatus.INACTIVE.name())
                .build();

        when(roleRepository.findByName(Roles.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(userRepository.findByEmail("deleted.user@example.com")).thenReturn(Optional.of(inactiveUser));
        when(userRepository.hasActiveGuideProfile(87L)).thenReturn(false);
        when(userRepository.hasActiveStaffProfile(87L)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_newpass123");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt_reactivated_token");
        when(userService.mapToUserResponse(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return UserResponse.builder()
                    .id(u.getId())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .role(u.getRole().getName())
                    .status(u.getStatus())
                    .build();
        });

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("jwt_reactivated_token", response.getToken());
        assertEquals(87L, response.getUser().getId());
        assertEquals("Reactivated Customer", response.getUser().getFullName());
        assertEquals(Roles.CUSTOMER, response.getUser().getRole());
        assertEquals(UserStatus.ACTIVE.name(), response.getUser().getStatus());
    }

    @Test
    void testLoginCustomer_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("  Customer@Example.com ")
                .password("secret123")
                .build();

        Role customerRole = Role.builder().id(4L).name(Roles.CUSTOMER).build();
        User activeCustomer = User.builder()
                .id(50L)
                .fullName("Customer A")
                .email("customer@example.com")
                .role(customerRole)
                .status(UserStatus.ACTIVE.name())
                .build();

        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(activeCustomer));
        when(jwtService.generateToken(activeCustomer)).thenReturn("jwt_login_token");
        when(userService.mapToUserResponse(activeCustomer)).thenReturn(
                UserResponse.builder()
                        .id(50L)
                        .fullName("Customer A")
                        .email("customer@example.com")
                        .role(Roles.CUSTOMER)
                        .status(UserStatus.ACTIVE.name())
                        .build()
        );

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt_login_token", response.getToken());
        assertEquals("customer@example.com", response.getUser().getEmail());
    }

    @Test
    void testOAuth2FirstLogin_GeneratesTempPasswordAndSendsEmail() throws Exception {
        EmailService emailService = mock(EmailService.class);
        OAuth2AuthenticationSuccessHandler handler = new OAuth2AuthenticationSuccessHandler(
                userRepository, roleRepository, passwordEncoder, jwtService, emailService
        );

        Role customerRole = Role.builder().id(4L).name(Roles.CUSTOMER).build();
        when(userRepository.findByEmail("google.customer@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(Roles.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_temp_pwd");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(300L);
            return u;
        });
        when(jwtService.generateToken(any(User.class))).thenReturn("oauth_jwt_token");

        org.springframework.security.oauth2.core.user.OAuth2User oauth2User = mock(org.springframework.security.oauth2.core.user.OAuth2User.class);
        when(oauth2User.getAttribute("email")).thenReturn("Google.Customer@gmail.com");
        when(oauth2User.getAttribute("name")).thenReturn("Google Customer");

        org.springframework.security.core.Authentication authentication = mock(org.springframework.security.core.Authentication.class);
        when(authentication.getPrincipal()).thenReturn(oauth2User);

        jakarta.servlet.http.HttpServletRequest servletRequest = mock(jakarta.servlet.http.HttpServletRequest.class);
        jakarta.servlet.http.HttpServletResponse servletResponse = mock(jakarta.servlet.http.HttpServletResponse.class);

        handler.onAuthenticationSuccess(servletRequest, servletResponse, authentication);

        ArgumentCaptor<String> rawPwdCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendCustomerGoogleWelcomeEmail(
                org.mockito.ArgumentMatchers.eq("google.customer@gmail.com"),
                org.mockito.ArgumentMatchers.eq("Google Customer"),
                rawPwdCaptor.capture()
        );
        assertEquals(10, rawPwdCaptor.getValue().length());
        verify(servletResponse).sendRedirect("http://localhost:3000/passport?token=oauth_jwt_token");
    }
}
