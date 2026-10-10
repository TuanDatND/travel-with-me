package com.coc.sba_treektour.tour.config;

import com.coc.sba_treektour.common.config.JwtAuthenticationFilter;
import com.coc.sba_treektour.common.config.JwtService;
import com.coc.sba_treektour.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.Map;

@Configuration
public class TourSecurityConfig {
    private final ObjectMapper mapper;

    /** Dùng bộ chuyển JSON của Spring để ghi lỗi bảo mật theo response chung. */
    public TourSecurityConfig(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Phân quyền tour, lịch và vé; dùng JWT chung và kiểm tra tài khoản ACTIVE. */
    @Bean
    @Order(1)
    SecurityFilterChain tourSecurity(
            HttpSecurity http, JwtService jwtService, UserDetailsService accounts)
            throws Exception {
        http.securityMatcher(
                        "/api/events",
                        "/api/events/**",
                        "/api/guides",
                        "/api/guides/**",
                        "/api/admin/events",
                        "/api/admin/events/**",
                        "/api/admin/guides",
                        "/api/admin/guides/**",
                        "/api/schedules",
                        "/api/schedules/**",
                        "/api/participants/**")
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.GET,
                                                "/api/events",
                                                "/api/events/*",
                                                "/api/guides",
                                                "/api/guides/*")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/schedules",
                                                "/api/schedules/*")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST, "/api/schedules/*/register")
                                        .authenticated()
                                        .requestMatchers(
                                                HttpMethod.GET, "/api/schedules/*/participants")
                                        .hasAnyAuthority("ADMIN", "STAFF", "GUIDE")
                                        .requestMatchers(
                                                HttpMethod.PATCH,
                                                "/api/participants/*/check-in",
                                                "/api/participants/*/no-show")
                                        .hasAnyAuthority("ADMIN", "STAFF", "GUIDE")
                                        .requestMatchers(
                                                HttpMethod.PATCH,
                                                "/api/participants/*/confirm-payment")
                                        .hasAnyAuthority("ADMIN", "STAFF")
                                        .requestMatchers("/api/participants/**")
                                        .authenticated()
                                        .requestMatchers("/api/schedules", "/api/schedules/**")
                                        .hasAnyAuthority("ADMIN", "STAFF")
                                        .requestMatchers("/api/admin/**")
                                        .hasAuthority("ADMIN")
                                        .anyRequest()
                                        .denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(
                        tourJwtFilter(jwtService, accounts),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, exception) ->
                                                        writeError(
                                                                response,
                                                                401,
                                                                "UNAUTHENTICATED",
                                                                "Login required"))
                                        .accessDeniedHandler(
                                                (request, response, exception) -> {
                                                    var authentication =
                                                            SecurityContextHolder.getContext()
                                                                    .getAuthentication();
                                                    if (authentication == null
                                                            || authentication
                                                                    instanceof
                                                                    AnonymousAuthenticationToken)
                                                        writeError(
                                                                response,
                                                                401,
                                                                "UNAUTHENTICATED",
                                                                "Login required");
                                                    else
                                                        writeError(
                                                                response,
                                                                403,
                                                                "FORBIDDEN",
                                                                "Access denied");
                                                }));
        // API chỉ nhận Bearer token, không dùng cookie hoặc HTTP Basic để xác thực.
        return http.build();
    }

    /** Tái sử dụng JWT của member 1 và kiểm tra trạng thái tài khoản trước khi cấp quyền tour. */
    private JwtAuthenticationFilter tourJwtFilter(
            JwtService jwtService, UserDetailsService accounts) {
        return new JwtAuthenticationFilter(
                jwtService,
                email -> {
                    var account = accounts.loadUserByUsername(email);
                    if (!account.isEnabled()
                            || !account.isAccountNonLocked()
                            || !account.isAccountNonExpired()
                            || !account.isCredentialsNonExpired()) {
                        throw new UsernameNotFoundException("Account is not active");
                    }
                    // Member 1 đã tải role EAGER; giữ principal gốc để các API lấy được userId.
                    return account;
                });
    }

    /** Ghi lỗi xác thực hoặc phân quyền dưới dạng JSON thống nhất. */
    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter()
                .write(
                        mapper.writeValueAsString(
                                new ApiResponse<>(
                                        status,
                                        message,
                                        Map.of("code", code, "fieldErrors", Map.of()),
                                        OffsetDateTime.now())));
    }
}
