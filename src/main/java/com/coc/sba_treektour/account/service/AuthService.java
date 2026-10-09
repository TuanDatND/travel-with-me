package com.coc.sba_treektour.account.service;

import com.coc.sba_treektour.account.dto.AuthResponse;
import com.coc.sba_treektour.account.dto.LoginRequest;
import com.coc.sba_treektour.account.dto.RegisterRequest;
import com.coc.sba_treektour.account.entity.Role;
import com.coc.sba_treektour.account.entity.User;
import com.coc.sba_treektour.account.repository.RoleRepository;
import com.coc.sba_treektour.account.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already taken!");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(request.getPassword()) // Note: Should be hashed with PasswordEncoder
                .role(userRole)
                .status("ACTIVE")
                .build();

        User savedUser = userRepository.save(user);

        return AuthResponse.builder()
                .token("dummy-jwt-token-for-now") // Real JWT token will be generated here later
                .user(userService.mapToUserResponse(savedUser))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!request.getPassword().equals(user.getPassword())) { // Note: Should use PasswordEncoder.matches()
            throw new RuntimeException("Invalid email or password");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account is not active");
        }

        return AuthResponse.builder()
                .token("dummy-jwt-token-for-now") // Real JWT token will be generated here later
                .user(userService.mapToUserResponse(user))
                .build();
    }
}
