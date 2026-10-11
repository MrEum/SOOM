package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.*;
import java.net.http.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:recovery;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.mail.password=test-only",
        "soom.frontend-url=https://soom.example",
    }
)
class AccountRecoveryIntegrationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    Environment environment;

    @Autowired
    com.example.demo.service.JwtService jwt;

    @MockitoBean
    JavaMailSender mail;

    HttpClient client;

    @BeforeEach
    void setup() {
        client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
        jdbc.execute("DELETE FROM password_reset_token");
        jdbc.execute("DROP TABLE IF EXISTS `user`");
        jdbc.execute(
            "CREATE TABLE `user` (user_id INT AUTO_INCREMENT PRIMARY KEY,email VARCHAR(100),password VARCHAR(255),name VARCHAR(50),nickname VARCHAR(20),role VARCHAR(20),status VARCHAR(20),email_verified_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP,deleted_at TIMESTAMP,scheduled_delete_at TIMESTAMP)"
        );
        jdbc.update(
            "INSERT INTO `user` (email,password,name,nickname,role,status,email_verified_at) VALUES (?,?,?,?, 'USER','ACTIVE',CURRENT_TIMESTAMP)",
            "tester@example.com",
            passwords.encode("Soom1234!"),
            "Tester",
            "tester"
        );
    }

    URI uri(String path) {
        return URI.create(
            "http://localhost:" + environment.getProperty("local.server.port") + "/api/auth/" + path
        );
    }

    HttpResponse<String> post(String path, String body) throws Exception {
        String csrfBody = client
            .send(
                HttpRequest.newBuilder(uri("csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
            )
            .body();
        var csrf = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(csrfBody);
        assertTrue(csrf.find());
        return client.send(
            HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrf.group(1))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    String requestBody = "{\"nickname\":\"tester\",\"email\":\"tester@example.com\"}";
    String loginBody =
        "{\"email\":\"tester@example.com\",\"password\":\"Soom1234!\",\"rememberMe\":false}";

    String sendLink() throws Exception {
        assertEquals(200, post("password/request", requestBody).statusCode());
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(message.capture());
        SimpleMailMessage sent = message.getValue();
        assertArrayEquals(new String[] { "tester@example.com" }, sent.getTo());
        var token = Pattern.compile(
            "https://soom.example/reset-password\\?token=([A-Za-z0-9_-]{43})"
        ).matcher(sent.getText());
        assertTrue(token.find());
        return token.group(1);
    }

    @Test
    void adminLoginRequiresAdminBeforeIssuingCookie() throws Exception {
        var denied = post("admin/login", loginBody);
        assertEquals(403, denied.statusCode());
        assertTrue(
            denied
                .headers()
                .allValues("set-cookie")
                .stream()
                .noneMatch(value -> value.startsWith("SOOM_ACCESS="))
        );
        jdbc.update("UPDATE `user` SET role='ADMIN'");
        var accepted = post("admin/login", loginBody);
        assertEquals(200, accepted.statusCode());
        assertTrue(
            accepted
                .headers()
                .allValues("set-cookie")
                .stream()
                .anyMatch(value -> value.startsWith("SOOM_ACCESS="))
        );
    }

    @Test
    void rejectsWrongNicknameWithoutSendingMailOrCreatingToken() throws Exception {
        var response = post("password/request", requestBody.replace("\"tester\"", "\"wrong\""));
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("이메일과 닉네임이 가입 정보와 일치하지 않습니다."));
        verifyNoInteractions(mail);
        assertEquals(
            0,
            jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token", Integer.class)
        );
        assertNotEquals(200, post("find-id", requestBody).statusCode());
    }

    @Test
    void resetsPasswordOnceAndStoresOnlyTokenHash() throws Exception {
        String previousSession = jwt.issue(1, 1800);
        String token = sendLink();
        String stored = jdbc.queryForObject(
            "SELECT token_hash FROM password_reset_token",
            String.class
        );
        assertEquals(64, stored.length());
        assertNotEquals(token, stored);
        long lifetime = jdbc.queryForObject(
            "SELECT DATEDIFF('SECOND',sent_at,expires_at) FROM password_reset_token",
            Long.class
        );
        assertEquals(3600, lifetime);
        assertEquals(200, post("password/validate", "{\"token\":\"" + token + "\"}").statusCode());
        String body = "{\"token\":\"" + token + "\",\"password\":\"Changed123!\"}";
        assertEquals(200, post("password/reset", body).statusCode());
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class, () ->
            jwt.validate(previousSession)
        );
        assertEquals(400, post("password/reset", body).statusCode());
        assertEquals(401, post("login", loginBody).statusCode());
        assertEquals(
            200,
            post("login", loginBody.replace("Soom1234!", "Changed123!")).statusCode()
        );
        assertTrue(
            passwords.matches(
                "Changed123!",
                jdbc.queryForObject("SELECT password FROM `user`", String.class)
            )
        );
    }

    @Test
    void rejectsExpiredTokensAndWeakPasswords() throws Exception {
        String token = sendLink();
        assertEquals(
            400,
            post(
                "password/reset",
                "{\"token\":\"" + token + "\",\"password\":\"weak\"}"
            ).statusCode()
        );
        jdbc.update(
            "UPDATE password_reset_token SET expires_at=DATEADD('SECOND',-1,CURRENT_TIMESTAMP)"
        );
        assertEquals(400, post("password/validate", "{\"token\":\"" + token + "\"}").statusCode());
        assertEquals(
            400,
            post(
                "password/reset",
                "{\"token\":\"" + token + "\",\"password\":\"Changed123!\"}"
            ).statusCode()
        );
    }

    @Test
    void suppressesMismatchedAndRepeatedMailAndInvalidatesOldLink() throws Exception {
        assertEquals(
            400,
            post(
                "password/request",
                requestBody.replace("tester@example.com", "missing@example.com")
            ).statusCode()
        );
        verifyNoInteractions(mail);
        String old = sendLink();
        assertEquals(429, post("password/request", requestBody).statusCode());
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        jdbc.update(
            "UPDATE password_reset_token SET sent_at=DATEADD('SECOND',-61,CURRENT_TIMESTAMP)"
        );
        String replacement = sendLink();
        assertNotEquals(old, replacement);
        assertEquals(400, post("password/validate", "{\"token\":\"" + old + "\"}").statusCode());
    }

    @Test
    void mailFailureRollsBackToken() throws Exception {
        doThrow(new MailSendException("SMTP unavailable"))
            .when(mail)
            .send(any(SimpleMailMessage.class));
        assertEquals(503, post("password/request", requestBody).statusCode());
        assertEquals(
            0,
            jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_token", Integer.class)
        );
    }

    @Test
    void concurrentRequestsConsumeTokenOnlyOnce() throws Exception {
        String token = sendLink();
        var barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> reset = () -> {
                barrier.await();
                return post(
                    "password/reset",
                    "{\"token\":\"" + token + "\",\"password\":\"Changed123!\"}"
                ).statusCode();
            };
            var first = executor.submit(reset);
            var second = executor.submit(reset);
            assertEquals(
                java.util.Set.of(200, 400),
                java.util.Set.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
            );
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recoveryRequiresCsrfAndRejectsInactiveAccounts() throws Exception {
        var response = client.send(
            HttpRequest.newBuilder(uri("password/request"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, response.statusCode());
        String token = sendLink();
        jdbc.update("UPDATE `user` SET status='SUSPENDED'");
        assertEquals(
            400,
            post(
                "password/reset",
                "{\"token\":\"" + token + "\",\"password\":\"Changed123!\"}"
            ).statusCode()
        );
    }
}
