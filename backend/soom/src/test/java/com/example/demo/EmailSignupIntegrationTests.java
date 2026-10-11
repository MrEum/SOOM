package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:emailflow;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.mail.password=fake-test-only",
    }
)
class EmailSignupIntegrationTests {

    @MockitoBean
    JavaMailSender mail;

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbc;

    HttpResponse<String> post(String path, String body) throws Exception {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" +
                        environment.getProperty("local.server.port") +
                        "/api/auth/" +
                        path
                )
            )
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void completeFlowRequiresVerificationAndRejectsReuse() throws Exception {
        jdbc.execute(
            "CREATE TABLE `user` (user_id INT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(100) UNIQUE NOT NULL, password VARCHAR(255) NOT NULL, name VARCHAR(50) NOT NULL, nickname VARCHAR(20) UNIQUE NOT NULL, role VARCHAR(20), status VARCHAR(20), created_at TIMESTAMP, updated_at TIMESTAMP, email_verified_at TIMESTAMP)"
        );
        String signup =
            "{\"email\":\"flow@example.com\",\"password\":\"Soom1234!\",\"name\":\"Tester\",\"nickname\":\"flow\",\"verificationToken\":\"TOKEN\",\"recoveryAnswers\":[\"blue\",\"pizza\",\"seoul\",\"book\",\"pilot\"]}";
        assertEquals(400, post("signup", signup).statusCode());
        var sent = post("email/send", "{\"email\":\" FLOW@example.com \"}");
        assertEquals(200, sent.statusCode(), sent.body());
        assertEquals(429, post("email/send", "{\"email\":\"flow@example.com\"}").statusCode());
        var capture = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(capture.capture());
        assertEquals("flow@example.com", capture.getValue().getTo()[0]);
        var matcher = java.util.regex.Pattern.compile("[0-9]{6}").matcher(
            capture.getValue().getText()
        );
        assertTrue(matcher.find());
        String code = matcher.group();
        assertFalse(sent.body().contains(code));
        var verified = post(
            "email/verify",
            "{\"email\":\"flow@example.com\",\"code\":\"" + code + "\"}"
        );
        assertEquals(200, verified.statusCode(), verified.body());
        var tokenMatcher = java.util.regex.Pattern.compile(
            "\"verificationToken\":\"([^\"]+)\""
        ).matcher(verified.body());
        assertTrue(tokenMatcher.find());
        String token = tokenMatcher.group(1);
        var created = post("signup", signup.replace("TOKEN", token));
        assertEquals(201, created.statusCode(), created.body());
        assertNotNull(
            jdbc.queryForObject(
                "SELECT email_verified_at FROM `user` WHERE email='flow@example.com'",
                java.sql.Timestamp.class
            )
        );
        assertEquals(400, post("signup", signup.replace("TOKEN", token)).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
    }
}
