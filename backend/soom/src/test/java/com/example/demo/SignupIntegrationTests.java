package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:signup;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
class SignupIntegrationTests {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.example.demo.service.EmailVerificationService verification;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    Environment environment;

    HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void schema() {
        jdbc.execute("DELETE FROM email_recovery_hint");
        org.mockito.Mockito.doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        })
            .when(verification)
            .withVerifiedEmail(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
            );
        jdbc.execute("DROP TABLE IF EXISTS `user`");
        jdbc.execute(
            "CREATE TABLE `user` (user_id INT AUTO_INCREMENT PRIMARY KEY, email VARCHAR(100) UNIQUE NOT NULL, password VARCHAR(255) NOT NULL, name VARCHAR(50) NOT NULL, nickname VARCHAR(20) UNIQUE NOT NULL, role VARCHAR(20), status VARCHAR(20), created_at TIMESTAMP, updated_at TIMESTAMP, email_verified_at TIMESTAMP)"
        );
    }

    URI uri(String path) {
        return URI.create(
            "http://localhost:" + environment.getProperty("local.server.port") + path
        );
    }

    HttpResponse<String> post(String body) throws Exception {
        return client.send(
            HttpRequest.newBuilder(uri("/api/auth/signup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    String valid =
        "{\"email\":\" New@Example.com \",\"password\":\"Soom1234!\",\"name\":\"Tester\",\"nickname\":\"tester\",\"verificationToken\":\"test-proof\",\"recoveryAnswers\":[\"blue\",\"pizza\",\"seoul\",\"book\",\"pilot\"]}";

    @Test
    void createsHashedAccountAndRejectsDuplicates() throws Exception {
        var response = post(valid);
        assertEquals(201, response.statusCode(), response.body());
        String hash = jdbc.queryForObject(
            "SELECT password FROM `user` WHERE email='new@example.com'",
            String.class
        );
        assertTrue(encoder.matches("Soom1234!", hash));
        assertFalse(response.body().contains(hash));
        assertEquals(409, post(valid).statusCode());
        assertEquals(409, post(valid.replace("New@Example.com", "other@example.com")).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
    }

    @Test
    void invalidRequestsDoNotInsert() throws Exception {
        assertEquals(400, post(valid.replace("Soom1234!", "weak")).statusCode());
        assertEquals(400, post(valid.replace("tester", " ")).statusCode());
        assertEquals(400, post("{").statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
    }

    @Test
    void availabilityAndSecurity() throws Exception {
        assertEquals(201, post(valid).statusCode());
        var check = client.send(
            HttpRequest.newBuilder(uri("/api/auth/check-email?email=new@example.com"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, check.statusCode());
        assertTrue(check.body().contains("false"));
        var users = client.send(
            HttpRequest.newBuilder(uri("/users")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(401, users.statusCode());
        var cors = client.send(
            HttpRequest.newBuilder(uri("/api/auth/signup"))
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(
            "http://localhost:5173",
            cors.headers().firstValue("Access-Control-Allow-Origin").orElse("")
        );
    }

    @Test
    void bothAvailabilityEndpointsBindExplicitNames() throws Exception {
        for (String field : new String[] { "email", "nickname" }) {
            var method = com.example.demo.controller.SignupController.class.getMethod(
                field,
                String.class
            );
            var binding = method
                .getParameters()[0]
                .getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
            assertTrue(
                field.equals(binding.value()) ||
                    (binding.value().isEmpty() && method.getParameters()[0].isNamePresent()),
                "Requires explicit binding or compiler parameter metadata"
            );
            String value = field.equals("email") ? "unused@example.com" : "unused";
            var response = client.send(
                HttpRequest.newBuilder(uri("/api/auth/check-" + field + "?" + field + "=" + value))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("true"));
            var invalid = client.send(
                HttpRequest.newBuilder(uri("/api/auth/check-" + field + "?" + field + "="))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(400, invalid.statusCode());
            assertFalse(invalid.body().contains("비밀번호"));
        }
        assertEquals(201, post(valid).statusCode());
        var duplicate = client.send(
            HttpRequest.newBuilder(uri("/api/auth/check-nickname?nickname=tester")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, duplicate.statusCode());
        assertTrue(duplicate.body().contains("false"));
    }

    @Test
    void allowsBothLocalFrontendPortsAndRejectsOtherOrigins() throws Exception {
        for (String origin : new String[] {
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5174",
        }) {
            var response = client.send(
                HttpRequest.newBuilder(uri("/api/auth/email/send"))
                    .header("Origin", origin)
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "content-type")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            assertEquals(200, response.statusCode(), response.body());
            assertEquals(
                origin,
                response.headers().firstValue("Access-Control-Allow-Origin").orElse("")
            );
        }
        var rejected = client.send(
            HttpRequest.newBuilder(uri("/api/auth/email/send"))
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, rejected.statusCode());
        assertTrue(rejected.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
    }
}
