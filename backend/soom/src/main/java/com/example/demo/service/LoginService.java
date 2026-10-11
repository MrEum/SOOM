package com.example.demo.service;

import com.example.demo.dto.request.LoginRequest;
import com.example.demo.dto.response.CurrentUserResponse;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public LoginService(UserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public CurrentUserResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email()).orElse(null);
        String passwordHash = user == null ? dummyHash : user.password();
        boolean matches = passwords.matches(request.password(), passwordHash);
        if (user == null || !matches) {
            throw new BadCredentialsException("이메일 또는 비밀번호를 확인해주세요.");
        }
        return requireActiveUser(user);
    }

    public CurrentUserResponse current(int id) {
        return requireActiveUser(
            users
                .findById(id)
                .orElseThrow(() -> new BadCredentialsException("다시 로그인해주세요."))
        );
    }

    public CurrentUserResponse adminLogin(LoginRequest request) {
        CurrentUserResponse user = login(request);
        if (!"ADMIN".equals(user.role())) {
            throw new DisabledException("관리자 계정으로 로그인해주세요.");
        }
        return user;
    }

    private CurrentUserResponse requireActiveUser(User user) {
        if (!"ACTIVE".equals(user.status()) || user.deletedAt() != null) {
            throw new DisabledException("정지 또는 탈퇴한 계정입니다.");
        }
        if (user.emailVerifiedAt() == null) {
            throw new DisabledException("이메일 인증이 완료되지 않은 계정입니다.");
        }
        return new CurrentUserResponse(
            user.userId(),
            user.email(),
            user.name(),
            user.nickname(),
            user.role()
        );
    }
}
