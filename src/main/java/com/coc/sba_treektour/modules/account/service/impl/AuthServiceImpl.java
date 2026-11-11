package com.coc.sba_treektour.modules.account.service.impl;

import com.coc.sba_treektour.common.config.JwtService;
import com.coc.sba_treektour.common.security.Roles;
import com.coc.sba_treektour.modules.account.dto.request.LoginRequest;
import com.coc.sba_treektour.modules.account.dto.request.RegisterRequest;
import com.coc.sba_treektour.modules.account.dto.response.AuthResponse;
import com.coc.sba_treektour.modules.account.entity.Role;
import com.coc.sba_treektour.modules.account.entity.User;
import com.coc.sba_treektour.modules.account.enums.UserStatus;
import com.coc.sba_treektour.modules.account.exception.InvalidAccountException;
import com.coc.sba_treektour.modules.account.exception.UserNotFoundException;
import com.coc.sba_treektour.modules.account.repository.RoleRepository;
import com.coc.sba_treektour.modules.account.repository.UserRepository;
import com.coc.sba_treektour.modules.account.service.AuthService;
import com.coc.sba_treektour.modules.account.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        Role userRole = roleRepository.findByName(Roles.CUSTOMER)
                .orElseThrow(() -> new InvalidAccountException("Default role CUSTOMER not found"));

        User existingUser = userRepository.findByEmail(normalizedEmail).orElse(null);
        User user;

        if (existingUser != null) {
            if (!UserStatus.INACTIVE.name().equals(existingUser.getStatus())) {
                throw new InvalidAccountException("Email is already in use in the system!");
            }
            Long existingId = existingUser.getId();
            if (existingId != null && (userRepository.hasActiveGuideProfile(existingId) || userRepository.hasActiveStaffProfile(existingId))) {
                throw new InvalidAccountException("This email belongs to an active staff member or tour guide!");
            }
            existingUser.setFullName(request.getFullName().trim());
            existingUser.setEmail(normalizedEmail);
            existingUser.setPhone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null);
            existingUser.setPassword(passwordEncoder.encode(request.getPassword()));
            existingUser.setRole(userRole);
            existingUser.setStatus(UserStatus.ACTIVE.name());
            user = existingUser;
        } else {
            user = User.builder()
                    .fullName(request.getFullName().trim())
                    .email(normalizedEmail)
                    .phone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null)
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(userRole)
                    .status(UserStatus.ACTIVE.name())
                    .build();
        }

        User savedUser = userRepository.save(user);
        String jwtToken = jwtService.generateToken(savedUser);

        return AuthResponse.builder()
                .token(jwtToken)
                .user(userService.mapToUserResponse(savedUser))
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        normalizedEmail,
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("User account not found"));

        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new InvalidAccountException("Account has been locked or deactivated");
        }

        String jwtToken = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(jwtToken)
                .user(userService.mapToUserResponse(user))
                .build();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
