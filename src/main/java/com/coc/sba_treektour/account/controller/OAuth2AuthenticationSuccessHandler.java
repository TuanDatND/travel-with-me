package com.coc.sba_treektour.account.controller;

import com.coc.sba_treektour.account.entity.Role;
import com.coc.sba_treektour.account.entity.User;
import com.coc.sba_treektour.account.repository.RoleRepository;
import com.coc.sba_treektour.account.repository.UserRepository;
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

        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(
                "<html><body>" +
                "<h2>Đăng nhập thành công!</h2>" +
                "<p>Copy đoạn Token bên dưới và dán vào Swagger:</p>" +
                "<textarea rows='5' cols='100' readonly>" + jwtToken + "</textarea>" +
                "<br><br><a href='http://localhost:8080/swagger-ui/index.html'>Quay lại Swagger</a>" +
                "</body></html>"
        );
    }
}
