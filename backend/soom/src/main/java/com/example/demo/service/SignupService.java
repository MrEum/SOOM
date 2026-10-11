package com.example.demo.service;

import com.example.demo.dto.request.SignupRequest;
import com.example.demo.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SignupService {

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final EmailVerificationService verification;
    private final TransactionTemplate transaction;
    private final EmailRecoveryService emailRecovery;

    public SignupService(
        UserRepository users,
        PasswordEncoder passwords,
        EmailVerificationService verification,
        TransactionTemplate transaction,
        EmailRecoveryService emailRecovery
    ) {
        this.users = users;
        this.passwords = passwords;
        this.verification = verification;
        this.transaction = transaction;
        this.emailRecovery = emailRecovery;
    }

    public boolean emailExists(String email) {
        return users.existsByEmail(email);
    }

    public boolean nicknameExists(String nickname) {
        return users.existsByNickname(nickname);
    }

    public void signup(SignupRequest request) {
        verification.withVerifiedEmail(request.email(), request.verificationToken(), () ->
            transaction.executeWithoutResult(status -> {
                if (
                    users.existsByEmail(request.email()) ||
                    users.existsByNickname(request.nickname())
                ) {
                    throw new DuplicateKeyException("Email or nickname already exists");
                }

                String passwordHash = passwords.encode(request.password());
                var answerHashes = emailRecovery.hashAnswers(request.recoveryAnswers());
                int userId = users.insert(
                    request.email(),
                    passwordHash,
                    request.name(),
                    request.nickname()
                );
                emailRecovery.saveAnswers(userId, answerHashes);
            })
        );
    }
}
