package com.coc.sba_treektour.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/payments/zalopay/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/payments/zalopay/**").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
