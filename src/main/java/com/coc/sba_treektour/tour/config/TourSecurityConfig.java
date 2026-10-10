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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    /** Cho khách xem nội dung công khai và xác thực ADMIN bằng JWT chung của member 1. */
    @Bean
    @Order(1)
    SecurityFilterChain tourSecurity(
            HttpSecurity http,
            JwtService jwtService,
            UserDetailsService accounts,
            PlatformTransactionManager transactionManager)
            throws Exception {
        http.securityMatcher(
                        "/api/events",
                        "/api/events/**",
                        "/api/guides",
                        "/api/guides/**",
                        "/api/admin/events",
                        "/api/admin/events/**",
                        "/api/admin/guides",
                        "/api/admin/guides/**")
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.GET,
                                                "/api/events",
                                                "/api/events/*",
                                                "/api/guides",
                                                "/api/guides/*")
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .denyAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(
                        tourJwtFilter(jwtService, accounts, transactionManager),
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

    /** Đọc quyền LAZY trong transaction và chặn tài khoản bị khóa trước khi xác thực JWT. */
    private JwtAuthenticationFilter tourJwtFilter(
            JwtService jwtService,
            UserDetailsService accounts,
            PlatformTransactionManager transactionManager) {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);
        return new JwtAuthenticationFilter(
                jwtService,
                email ->
                        transaction.execute(
                                status -> {
                                    var account = accounts.loadUserByUsername(email);
                                    if (!account.isEnabled()
                                            || !account.isAccountNonLocked()
                                            || !account.isAccountNonExpired()
                                            || !account.isCredentialsNonExpired()) {
                                        throw new UsernameNotFoundException(
                                                "Account is not active");
                                    }
                                    // Sao chép quyền trong transaction để filter không truy cập
                                    // entity LAZY sau đó.
                                    return org.springframework.security.core.userdetails.User
                                            .withUserDetails(account)
                                            .build();
                                }));
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
