package com.example.demo.controller;

import com.example.demo.config.JwtCookieFilter;
import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.response.*;
import com.example.demo.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private final LoginService login;
    private final JwtService jwt;
    private final boolean secure;

    public LoginController(
        LoginService login,
        JwtService jwt,
        @Value("${soom.cookie.secure:false}") boolean secure
    ) {
        this.login = login;
        this.jwt = jwt;
        this.secure = secure;
    }

    private String cookie(String value, long age) {
        return ResponseCookie.from("SOOM_ACCESS", value)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api")
            .maxAge(age)
            .build()
            .toString();
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/login")
    public CurrentUserResponse login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        var user = login.login(request);
        return createSession(user, request, httpRequest, response);
    }

    @PostMapping("/admin/login")
    public CurrentUserResponse adminLogin(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        var user = login.adminLogin(request);
        return createSession(user, request, httpRequest, response);
    }

    private CurrentUserResponse createSession(
        CurrentUserResponse user,
        LoginRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        String previous = JwtCookieFilter.cookie(httpRequest);
        if (previous != null) {
            jwt.revoke(previous);
        }
        long ttl = request.rememberMe() ? 604800 : 1800;
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            cookie(jwt.issue(user.userId(), ttl), request.rememberMe() ? ttl : -1)
        );
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return user;
    }

    @GetMapping("/me")
    public CurrentUserResponse me(
        @AuthenticationPrincipal CurrentUserResponse user,
        HttpServletResponse response
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return user;
    }

    @PostMapping("/logout")
    public MessageResponse logout(HttpServletRequest request, HttpServletResponse response) {
        String value = JwtCookieFilter.cookie(request);
        if (value != null) {
            jwt.revoke(value);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", 0));
        return new MessageResponse("로그아웃되었습니다.");
    }
}
