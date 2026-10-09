package com.coc.sba_treektour.tour.config;

import jakarta.servlet.http.HttpServletResponse;
import com.coc.sba_treektour.common.response.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@Configuration
public class TourSecurityConfig {
    private final ObjectMapper mapper;

    /** Dùng bộ chuyển JSON của Spring để ghi lỗi bảo mật theo response chung. */
    public TourSecurityConfig(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Cho khách đọc API công khai, yêu cầu ADMIN cho quản trị và giữ CSRF khi ghi dữ liệu. */
    @Bean
    @Order(1)
    SecurityFilterChain tourSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/api/events",
                        "/api/events/**",
                        "/api/guides",
                        "/api/guides/**",
                        "/api/admin/events",
                        "/api/admin/events/**",
                        "/api/admin/guides",
                        "/api/admin/guides/**",
                        "/api/tour/csrf")
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                HttpMethod.GET,
                                                "/api/events",
                                                "/api/events/*",
                                                "/api/guides",
                                                "/api/guides/*",
                                                "/api/tour/csrf")
                                        .permitAll()
                                        .requestMatchers("/api/admin/**")
                                        .hasRole("ADMIN")
                                        .anyRequest()
                                        .denyAll())
                .httpBasic(Customizer.withDefaults())
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
                                                                "Access denied or invalid CSRF token");
                                                }));
        // Giữ CSRF cho cả xác thực bằng phiên trình duyệt và HTTP Basic.
        return http.build();
    }

    /** Giữ bảo vệ mặc định cho đường dẫn khác đến khi module tài khoản cung cấp cấu hình chung. */
    @Bean
    @Order(Integer.MAX_VALUE)
    SecurityFilterChain applicationFallbackSecurity(HttpSecurity http) throws Exception {
        // Giữ bảo vệ mặc định ngoài module đến khi phần tài khoản cung cấp filter chain chung.
        return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .formLogin(Customizer.withDefaults())
                .build();
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

    @RestController
    static class CsrfController {
        /** Trả CSRF token để client gửi cùng cookie phiên trong các request thay đổi dữ liệu. */
        @GetMapping("/api/tour/csrf")
        public ApiResponse<CsrfToken> csrf(CsrfToken token) {
            return ApiResponse.success(token);
        }
    }
}
