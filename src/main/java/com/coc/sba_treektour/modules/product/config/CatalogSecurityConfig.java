package com.coc.sba_treektour.modules.product.config;

import com.coc.sba_treektour.common.config.JwtAuthenticationFilter;
import com.coc.sba_treektour.common.config.JwtService;
import com.coc.sba_treektour.common.response.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class CatalogSecurityConfig {
    @Bean @Order(2)
    SecurityFilterChain catalogSecurity(HttpSecurity http, JwtService jwt, UserDetailsService accounts, ObjectMapper mapper) throws Exception {
        return http.securityMatcher("/api/products", "/api/products/**", "/api/admin/products", "/api/admin/products/**",
                        "/api/inventory", "/api/inventory/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET,
                                "/api/products", "/api/products/*", "/api/inventory/availability", "/api/inventory/sale-availability").permitAll()
                        .requestMatchers("/api/admin/products", "/api/admin/products/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/inventory/rentals").authenticated()
                        .requestMatchers("/api/inventory/**").hasAnyAuthority("ADMIN", "STAFF").anyRequest().denyAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwt, email -> {
                    var account = accounts.loadUserByUsername(email);
                    if (!account.isEnabled() || !account.isAccountNonLocked()) throw new UsernameNotFoundException("Tài khoản không hoạt động");
                    return account;
                }), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(mapper.writeValueAsString(ApiResponse.error(401, "Cần đăng nhập")));
                }).accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(mapper.writeValueAsString(ApiResponse.error(403, "Không có quyền truy cập")));
                })).build();
    }
}
