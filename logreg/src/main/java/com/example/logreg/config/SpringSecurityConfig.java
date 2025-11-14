package com.example.logreg.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SpringSecurityConfig {

    @Resource
    private DBUserDetailsManager dbUserDetailsManager;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // 注册 AuthenticationManager，并手动指定 UserDetailsService 和 PasswordEncoder
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);
        builder.userDetailsService(dbUserDetailsManager).passwordEncoder(passwordEncoder);
        return builder.build();
    }


    // 安全过滤器链
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class) // 使用注入的JWT过滤器
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/user/captcha","/api/user/captcha/verify","/api/user/login", "/api/user/register").permitAll()
                        .requestMatchers("/doc.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

}
