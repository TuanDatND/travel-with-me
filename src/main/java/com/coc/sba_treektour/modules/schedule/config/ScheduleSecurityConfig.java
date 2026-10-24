package com.coc.sba_treektour.modules.schedule.config;

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
public class ScheduleSecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain scheduleSecurity(HttpSecurity http, JwtService jwt, UserDetailsService accounts, ObjectMapper mapper) throws Exception {
        return http.securityMatcher("/api/schedules", "/api/schedules/**", "/api/participants", "/api/participants/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/schedules/my-assignments", "/api/schedules/my-assignments/*")
                            .hasAuthority("GUIDE")
                        .requestMatchers(HttpMethod.GET, "/api/schedules", "/api/schedules/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/schedules/*/register").hasAuthority("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/schedules/*/participants", "/api/schedules/*/histories")
                            .hasAnyAuthority("ADMIN", "STAFF", "GUIDE")
                        .requestMatchers(HttpMethod.PATCH, "/api/participants/*/check-in", "/api/participants/*/no-show")
                            .hasAnyAuthority("ADMIN", "STAFF", "GUIDE")
                        .requestMatchers(HttpMethod.PATCH, "/api/participants/*/confirm-payment").hasAnyAuthority("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/participants/payment-reviews").hasAnyAuthority("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/api/participants/*", "/api/participants/by-user/*", "/api/participants/*/histories")
                            .hasAnyAuthority("ADMIN", "STAFF", "CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/participants/*/cancel").hasAnyAuthority("ADMIN", "STAFF", "CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/schedules").hasAnyAuthority("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PUT, "/api/schedules/*").hasAnyAuthority("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/api/schedules/*/reschedule").hasAnyAuthority("ADMIN", "STAFF")
                        .requestMatchers(HttpMethod.PATCH, "/api/schedules/*/status").hasAnyAuthority("ADMIN", "STAFF")
                        .anyRequest().denyAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwt, email -> {
                    var user = accounts.loadUserByUsername(email);
                    if (!user.isEnabled() || !user.isAccountNonLocked()) throw new UsernameNotFoundException("Tài khoản không hoạt động");
                    return user;
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
