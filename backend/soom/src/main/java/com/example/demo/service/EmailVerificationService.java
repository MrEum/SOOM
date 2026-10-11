package com.example.demo.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {

    public static class VerificationException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        public final int status;

        public VerificationException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private static class Challenge {

        String hash;
        String token;
        Instant sentAt;
        Instant expiresAt;
        int attempts;
    }

    private final Map<String, Challenge> challenges = new HashMap<>();
    private final Deque<Instant> sends = new ArrayDeque<>();
    private final SecureRandom random = new SecureRandom();
    private final JavaMailSender mail;
    private final PasswordEncoder encoder;
    private final String sender;
    private final String smtpPassword;
    private final Clock clock;

    @Autowired
    public EmailVerificationService(
        JavaMailSender mail,
        PasswordEncoder encoder,
        @Value("${spring.mail.username}") String sender,
        @Value("${spring.mail.password:}") String smtpPassword
    ) {
        this(mail, encoder, sender, smtpPassword, Clock.systemUTC());
    }

    EmailVerificationService(
        JavaMailSender mail,
        PasswordEncoder encoder,
        String sender,
        String smtpPassword,
        Clock clock
    ) {
        this.mail = mail;
        this.encoder = encoder;
        this.sender = sender;
        this.smtpPassword = smtpPassword;
        this.clock = clock;
    }

    private void cleanup(Instant now) {
        challenges.entrySet().removeIf(e -> !now.isBefore(e.getValue().expiresAt));
        while (!sends.isEmpty() && !sends.peekFirst().isAfter(now.minusSeconds(60)))
            sends.removeFirst();
    }

    public synchronized void send(String email) {
        Instant now = clock.instant();
        cleanup(now);
        Challenge old = challenges.get(email);
        if (
            old != null && now.isBefore(old.sentAt.plusSeconds(60))
        ) throw new VerificationException(429, "인증번호는 60초 후 다시 보낼 수 있습니다.");
        if (sends.size() >= 20 || challenges.size() >= 1000) throw new VerificationException(
            429,
            "발송 요청이 많습니다. 잠시 후 다시 시도해주세요."
        );
        if (smtpPassword.isBlank()) throw new VerificationException(
            503,
            "메일 서버 설정이 필요합니다. 관리자에게 문의해주세요."
        );
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        Challenge next = new Challenge();
        next.hash = encoder.encode(code);
        next.sentAt = now;
        next.expiresAt = now.plusSeconds(300);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        message.setSubject("[숨 SOOM] 회원가입 이메일 인증번호");
        message.setText(
            "숨 회원가입 인증번호는 " +
                code +
                " 입니다.\n5분 안에 입력해주세요.\n본인이 요청하지 않았다면 이 메일을 무시해주세요."
        );
        sends.addLast(now);
        try {
            mail.send(message);
        } catch (MailException e) {
            throw new VerificationException(
                503,
                "인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요."
            );
        }
        challenges.put(email, next);
    }

    public synchronized void sendPasswordReset(String email, String link) {
        Instant now = clock.instant();
        cleanup(now);
        if (sends.size() >= 20) throw new VerificationException(
            429,
            "발송 요청이 많습니다. 잠시 후 다시 시도해주세요."
        );
        if (smtpPassword.isBlank()) throw new VerificationException(
            503,
            "메일 서버 설정이 필요합니다. 관리자에게 문의해주세요."
        );
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(email);
        message.setSubject("[숨 SOOM] 비밀번호 재설정");
        message.setText(
            "아래 링크에서 새 비밀번호를 설정해주세요.\n\n" +
                link +
                "\n\n링크는 1시간 동안 한 번만 사용할 수 있습니다.\n본인이 요청하지 않았다면 이 메일을 무시해주세요."
        );
        sends.addLast(now);
        try {
            mail.send(message);
        } catch (MailException exception) {
            throw new VerificationException(
                503,
                "재설정 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요."
            );
        }
    }

    public synchronized String verify(String email, String code) {
        Instant now = clock.instant();
        cleanup(now);
        Challenge challenge = challenges.get(email);
        if (challenge == null) throw new VerificationException(
            400,
            "인증번호가 만료되었거나 발송되지 않았습니다. 다시 발송해주세요."
        );
        if (challenge.attempts >= 5) throw new VerificationException(
            429,
            "인증 시도 횟수를 초과했습니다. 인증번호를 다시 발송해주세요."
        );
        if (challenge.token != null) throw new VerificationException(
            400,
            "이미 확인한 인증번호입니다. 필요하면 다시 발송해주세요."
        );
        challenge.attempts++;
        if (!encoder.matches(code, challenge.hash)) throw new VerificationException(
            400,
            "인증번호가 일치하지 않습니다."
        );
        challenge.hash = null;
        challenge.token = UUID.randomUUID().toString();
        challenge.expiresAt = now.plusSeconds(600);
        return challenge.token;
    }

    public synchronized void withVerifiedEmail(String email, String token, Runnable createAccount) {
        cleanup(clock.instant());
        Challenge challenge = challenges.get(email);
        if (
            challenge == null ||
            token == null ||
            challenge.token == null ||
            !challenge.token.equals(token)
        ) throw new VerificationException(
            400,
            "이메일 인증을 완료해주세요. 인증 완료 후 10분 안에 가입해야 합니다."
        );
        createAccount.run();
        challenges.remove(email);
    }
}
