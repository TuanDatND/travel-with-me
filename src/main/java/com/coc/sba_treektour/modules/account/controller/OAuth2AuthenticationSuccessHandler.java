package com.coc.sba_treektour.modules.account.controller;

import com.coc.sba_treektour.common.config.JwtService;
import com.coc.sba_treektour.common.security.Roles;
import com.coc.sba_treektour.common.service.EmailService;
import com.coc.sba_treektour.common.util.PasswordGenerator;
import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.enums.UserStatus;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository,
                                              RoleRepository roleRepository,
                                              @Lazy PasswordEncoder passwordEncoder,
                                              JwtService jwtService,
                                              EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Google account does not have an email.");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String displayName = (name != null && !name.isBlank()) ? name.trim() : normalizedEmail;

        Optional<User> existingUserOpt = userRepository.findByEmail(normalizedEmail);
        User user;

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if (UserStatus.INACTIVE.name().equals(existingUser.getStatus())) {
                Role customerRole = roleRepository.findByName(Roles.CUSTOMER)
                        .orElseThrow(() -> new RuntimeException("Default role not found"));
                String rawPassword = PasswordGenerator.generateRandomPassword(10);
                existingUser.setFullName(displayName);
                existingUser.setPassword(passwordEncoder.encode(rawPassword));
                existingUser.setRole(customerRole);
                existingUser.setStatus(UserStatus.ACTIVE.name());
                user = userRepository.save(existingUser);
                emailService.sendCustomerGoogleWelcomeEmail(user.getEmail(), user.getFullName(), rawPassword);
            } else {
                user = existingUser;
            }
        } else {
            Role userRole = roleRepository.findByName(Roles.CUSTOMER)
                    .orElseThrow(() -> new RuntimeException("Default role not found"));
            String rawPassword = PasswordGenerator.generateRandomPassword(10);

            user = userRepository.save(User.builder()
                    .email(normalizedEmail)
                    .fullName(displayName)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(userRole)
                    .status(UserStatus.ACTIVE.name())
                    .build());

            emailService.sendCustomerGoogleWelcomeEmail(user.getEmail(), user.getFullName(), rawPassword);
        }

        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Your account has been locked or deactivated.");
            return;
        }

        String jwtToken = jwtService.generateToken(user);
        String roleName = (user.getRole() != null) ? user.getRole().getName() : Roles.CUSTOMER;

        String targetUrl = switch (roleName) {
            case Roles.ADMIN -> "http://localhost:3000/admin?token=" + jwtToken;
            case Roles.STAFF -> "http://localhost:3000/staff?token=" + jwtToken;
            case Roles.GUIDE -> "http://localhost:3000/guide/schedules?token=" + jwtToken;
            default -> "http://localhost:3000/passport?token=" + jwtToken;
        };

        response.sendRedirect(targetUrl);
    }
}
