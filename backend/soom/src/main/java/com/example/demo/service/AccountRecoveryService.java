package com.example.demo.service;

import com.example.demo.dto.request.RecoveryRequest;
import com.example.demo.model.User;
import com.example.demo.repository.PasswordResetRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailVerificationService.VerificationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountRecoveryService {

    private final UserRepository users;
    private final PasswordResetRepository tokens;
    private final PasswordEncoder passwords;
    private final EmailVerificationService mail;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public AccountRecoveryService(
        UserRepository users,
        PasswordResetRepository tokens,
        PasswordEncoder passwords,
        EmailVerificationService mail,
        @Value("${soom.frontend-url:http://localhost:5173}") String frontendUrl
    ) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.mail = mail;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void sendResetLink(RecoveryRequest request) {
        User user = activeUser(request.email());
        tokens.lockUser(user.userId());
        user = activeUser(request.email());
        if (!user.nickname().equals(request.nickname())) throw mismatch();
        Instant now = Instant.now();
        if (tokens.recentlySent(user.userId(), now)) throw new VerificationException(
            429,
            "이미 재설정 메일을 보냈습니다. 60초 후 다시 요청해주세요."
        );
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.replace(user.userId(), digest(token), now);
        mail.sendPasswordReset(user.email(), frontendUrl + "/reset-password?token=" + token);
    }

    public void validate(String token) {
        int userId = tokens.findUser(digest(token), Instant.now()).orElseThrow(this::invalidToken);
        users.findById(userId).filter(this::isActive).orElseThrow(this::invalidToken);
    }

    @Transactional
    public void reset(String token, String password) {
        String tokenHash = digest(token);
        int userId = tokens.findUser(tokenHash, Instant.now()).orElseThrow(this::invalidToken);
        tokens.lockUser(userId);
        users.findById(userId).filter(this::isActive).orElseThrow(this::invalidToken);
        String passwordHash = passwords.encode(password);
        if (!tokens.consume(tokenHash, Instant.now())) throw invalidToken();
        tokens.updatePassword(userId, passwordHash);
    }

    private User activeUser(String email) {
        return users.findByEmail(email).filter(this::isActive).orElseThrow(this::mismatch);
    }

    private boolean isActive(User user) {
        return (
            "ACTIVE".equals(user.status()) &&
            user.deletedAt() == null &&
            user.emailVerifiedAt() != null
        );
    }

    private VerificationException mismatch() {
        return new VerificationException(400, "이메일과 닉네임이 가입 정보와 일치하지 않습니다.");
    }

    private VerificationException invalidToken() {
        return new VerificationException(
            400,
            "만료되었거나 이미 사용한 링크입니다. 재설정 링크를 다시 요청해주세요."
        );
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
