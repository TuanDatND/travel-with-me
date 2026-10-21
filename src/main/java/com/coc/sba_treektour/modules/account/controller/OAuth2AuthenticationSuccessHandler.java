package com.coc.sba_treektour.modules.account.controller;

import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.common.config.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public OAuth2AuthenticationSuccessHandler(UserRepository userRepository, 
                                              RoleRepository roleRepository, 
                                              @Lazy PasswordEncoder passwordEncoder, 
                                              JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            Role userRole = roleRepository.findByName("CUSTOMER")
                    .orElseThrow(() -> new RuntimeException("Default role not found"));
            
            return userRepository.save(User.builder()
                    .email(email)
                    .fullName(name)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(userRole)
                    .status("ACTIVE")
                    .build());
        });

                String jwtToken = jwtService.generateToken(user);
        String roleName = (user.getRole() != null) ? user.getRole().getName() : "CUSTOMER";

        // Chuyển hướng trực tiếp sang đúng trang theo Role kèm JWT token
        String targetUrl = switch (roleName) {
            case "ADMIN" -> "http://localhost:3000/admin?token=" + jwtToken;
            case "STAFF" -> "http://localhost:3000/staff?token=" + jwtToken;
            default -> "http://localhost:3000/passport?token=" + jwtToken;
        };

        response.sendRedirect(targetUrl);
    }
}