package com.example.demo.service;

import com.example.demo.dto.request.FindEmailRequest;
import com.example.demo.repository.EmailRecoveryRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailVerificationService.VerificationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailRecoveryService {

    private final UserRepository users;
    private final EmailRecoveryRepository recovery;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public EmailRecoveryService(
        UserRepository users,
        EmailRecoveryRepository recovery,
        PasswordEncoder passwords
    ) {
        this.users = users;
        this.recovery = recovery;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(java.util.UUID.randomUUID().toString());
    }

    public List<String> hashAnswers(List<String> answers) {
        return answers
            .stream()
            .map(this::normalize)
            .map(answer -> answer.isEmpty() ? "" : passwords.encode(digest(answer)))
            .toList();
    }

    public void saveAnswers(int userId, List<String> hashes) {
        recovery.saveAnswers(userId, hashes);
    }

    @Transactional(noRollbackFor = VerificationException.class)
    public List<Integer> questions(String nickname) {
        if (
            !recovery.recordAttempt(digest("questions:" + normalize(nickname)), Instant.now())
        ) throw new VerificationException(
            429,
            "질문 확인 요청이 많습니다. 15분 후 다시 시도해주세요."
        );
        var user = users.findByNickname(nickname).orElse(null);
        if (
            user == null ||
            !"ACTIVE".equals(user.status()) ||
            user.deletedAt() != null ||
            user.emailVerifiedAt() == null
        ) throw new VerificationException(
            400,
            "등록된 힌트를 확인할 수 없습니다. 닉네임을 확인해주세요."
        );
        var hashes = recovery.findAnswers(user.userId());
        var selected = java.util.stream.IntStream.range(0, hashes.size())
            .filter(i -> !hashes.get(i).isEmpty())
            .map(i -> i + 1)
            .boxed()
            .toList();
        if (selected.isEmpty()) throw new VerificationException(
            400,
            "등록된 힌트를 확인할 수 없습니다. 닉네임을 확인해주세요."
        );
        return selected;
    }

    @Transactional(noRollbackFor = VerificationException.class)
    public String findEmail(FindEmailRequest request) {
        if (!recovery.recordAttempt(digest(normalize(request.nickname())), Instant.now())) {
            throw new VerificationException(
                429,
                "아이디 찾기는 15분 동안 5회까지 시도할 수 있습니다. 잠시 후 다시 시도해주세요."
            );
        }
        var user = users.findByNickname(request.nickname()).orElse(null);
        List<String> hashes = user == null ? List.of() : recovery.findAnswers(user.userId());
        boolean matches = hashes.stream().anyMatch(hash -> !hash.isEmpty());
        for (int index = 0; index < 5; index++) {
            String hash = hashes.size() == 5 ? hashes.get(index) : dummyHash;
            String answer = normalize(request.recoveryAnswers().get(index));
            boolean verified = passwords.matches(digest(answer), hash.isEmpty() ? dummyHash : hash);
            matches &= hash.isEmpty() ? answer.isEmpty() : !answer.isEmpty() && verified;
        }
        if (
            !matches ||
            user == null ||
            !"ACTIVE".equals(user.status()) ||
            user.deletedAt() != null ||
            user.emailVerifiedAt() == null
        ) {
            throw new VerificationException(
                400,
                "닉네임 또는 힌트 답변이 일치하지 않습니다. 회원가입 시 답변을 등록한 계정만 찾을 수 있습니다."
            );
        }
        String email = user.email();
        int separator = email.indexOf('@');
        int visible = Math.min(3, Math.max(0, separator - 1));
        return email.substring(0, visible) + "****" + email.substring(separator);
    }

    private String normalize(String answer) {
        return Normalizer.normalize(answer, Normalizer.Form.NFKC)
            .strip()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
