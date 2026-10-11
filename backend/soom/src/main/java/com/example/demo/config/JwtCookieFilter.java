package com.example.demo.config;

import com.example.demo.service.JwtService;
import com.example.demo.service.LoginService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtCookieFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final LoginService login;

    public JwtCookieFilter(JwtService jwt, LoginService login) {
        this.jwt = jwt;
        this.login = login;
    }

    public static String cookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if ("SOOM_ACCESS".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return (
            !"/api/auth/me".equals(path) &&
            !path.startsWith("/api/admin/") &&
            !path.equals("/api/quit-records") &&
            !path.startsWith("/api/quit-records/") &&
            !path.equals("/api/dashboard") &&
            !path.startsWith("/api/dashboard/") &&
            !path.equals("/api/community") &&
            !path.startsWith("/api/community/")
        );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        String value = cookie(request);
        if (value == null) {
            chain.doFilter(request, response);
            return;
        }

        try {
            var token = jwt.validate(value);
            var user = login.current(Integer.parseInt(token.getSubject()));
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | AuthenticationException | NumberFormatException e) {
            SecurityContextHolder.clearContext();
        } catch (DataAccessException e) {
            response.setStatus(503);
            response.setContentType("application/json;charset=UTF-8");
            response
                .getWriter()
                .write(
                    "{\"message\":\"로그인 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요.\"}"
                );
            return;
        }
        chain.doFilter(request, response);
    }
}
