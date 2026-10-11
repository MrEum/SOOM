package com.example.demo.config;

import com.example.demo.service.JwtService;
import com.example.demo.service.LoginService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, LoginService login)
        throws Exception {
        var csrfRepository = new CookieCsrfTokenRepository();
        csrfRepository.setCookieCustomizer(cookie ->
            cookie.httpOnly(true).sameSite("Lax").path("/")
        );
        return http
            .cors(cors -> cors.configurationSource(corsConfiguration()))
            .csrf(csrf ->
                csrf
                    .csrfTokenRepository(csrfRepository)
                    .ignoringRequestMatchers(
                        "/api/auth/signup",
                        "/api/auth/email/send",
                        "/api/auth/email/verify"
                    )
            )
            .addFilterBefore(
                new JwtCookieFilter(jwt, login),
                UsernamePasswordAuthenticationFilter.class
            )
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/login",
                        "/api/auth/admin/login",
                        "/api/auth/logout",
                        "/api/auth/find-id",
                        "/api/auth/find-id/questions",
                        "/api/auth/password/request",
                        "/api/auth/password/validate",
                        "/api/auth/password/reset"
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/me")
                    .authenticated()
                    .requestMatchers("/api/quit-records", "/api/quit-records/**")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/community",
                        "/api/community/catalog",
                        "/api/community/images/*"
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/community/*/view")
                    .permitAll()
                    .requestMatchers(
                        "/api/dashboard",
                        "/api/dashboard/**",
                        "/api/community",
                        "/api/community/**"
                    )
                    .authenticated()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/auth/signup",
                        "/api/auth/email/send",
                        "/api/auth/email/verify"
                    )
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/auth/check-email",
                        "/api/auth/check-nickname"
                    )
                    .permitAll()
                    .anyRequest()
                    .denyAll()
            )
            .exceptionHandling(e ->
                e
                    .accessDeniedHandler((request, response, exception) -> {
                        response.setStatus(403);
                        response.setContentType("application/json;charset=UTF-8");
                        response
                            .getWriter()
                            .write(
                                "{\"message\":\"접근 권한이 없거나 보안 검증에 실패했습니다.\"}"
                            );
                    })
                    .authenticationEntryPoint((request, response, exception) -> {
                        response.setStatus(401);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"message\":\"인증이 필요합니다.\"}");
                    })
            )
            .build();
    }

    private UrlBasedCorsConfigurationSource corsConfiguration() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(
            List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"
            )
        );
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
