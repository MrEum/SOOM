package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class EmailVerificationServiceTests {

    static class MutableClock extends Clock {

        Instant now = Instant.parse("2026-10-03T00:00:00Z");

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    JavaMailSender mail;
    EmailVerificationService service;
    MutableClock clock;

    @BeforeEach
    void setup() {
        mail = mock(JavaMailSender.class);
        clock = new MutableClock();
        service = new EmailVerificationService(
            mail,
            new BCryptPasswordEncoder(4),
            "sender@gmail.com",
            "fake-test-password",
            clock
        );
    }

    String code() {
        var capture = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(capture.capture());
        var matcher = java.util.regex.Pattern.compile("[0-9]{6}").matcher(
            capture.getValue().getText()
        );
        assertTrue(matcher.find());
        return matcher.group();
    }

    @Test
    void proofIsBoundToEmailAndConsumedAfterSuccessfulCommit() {
        service.send("a@example.com");
        String token = service.verify("a@example.com", code());
        assertThrows(EmailVerificationService.VerificationException.class, () ->
            service.withVerifiedEmail("b@example.com", token, () -> fail())
        );
        assertThrows(IllegalStateException.class, () ->
            service.withVerifiedEmail("a@example.com", token, () -> {
                throw new IllegalStateException("rollback");
            })
        );
        var saved = new AtomicBoolean();
        service.withVerifiedEmail("a@example.com", token, () -> saved.set(true));
        assertTrue(saved.get());
        assertThrows(EmailVerificationService.VerificationException.class, () ->
            service.withVerifiedEmail("a@example.com", token, () -> fail())
        );
    }

    @Test
    void limitsAttemptsAndResending() {
        service.send("a@example.com");
        String original = code();
        assertThrows(EmailVerificationService.VerificationException.class, () ->
            service.send("a@example.com")
        );
        String wrong = original.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertThrows(
            EmailVerificationService.VerificationException.class,
            () -> service.verify("a@example.com", wrong)
        );
        assertThrows(EmailVerificationService.VerificationException.class, () ->
            service.verify("a@example.com", original)
        );
        clock.now = clock.now.plusSeconds(61);
        service.send("a@example.com");
        assertNotNull(service.verify("a@example.com", code()));
    }

    @Test
    void expiresCodeAndProof() {
        service.send("a@example.com");
        String original = code();
        clock.now = clock.now.plusSeconds(300);
        assertThrows(EmailVerificationService.VerificationException.class, () ->
            service.verify("a@example.com", original)
        );
        service.send("a@example.com");
        String token = service.verify("a@example.com", code());
        clock.now = clock.now.plusSeconds(600);
        assertThrows(EmailVerificationService.VerificationException.class, () ->
            service.withVerifiedEmail("a@example.com", token, () -> fail())
        );
    }

    @Test
    void smtpFailureDoesNotCreateChallenge() {
        doThrow(new MailSendException("failure")).when(mail).send(any(SimpleMailMessage.class));
        assertEquals(
            503,
            assertThrows(EmailVerificationService.VerificationException.class, () ->
                service.send("a@example.com")
            ).status
        );
        assertThrows(EmailVerificationService.VerificationException.class, () ->
            service.verify("a@example.com", "000000")
        );
    }
}
