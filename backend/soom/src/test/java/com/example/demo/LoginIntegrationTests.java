package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.example.demo.service.JwtService;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:login;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
class LoginIntegrationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder encoder;

    @Autowired
    Environment env;

    @Autowired
    JwtService jwt;

    HttpClient client;
    String hash;

    @BeforeEach
    void setup() {
        client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
        jdbc.execute("DROP TABLE IF EXISTS `user`");
        jdbc.execute(
            "CREATE TABLE `user` (user_id INT AUTO_INCREMENT PRIMARY KEY,email VARCHAR(100),password VARCHAR(255),name VARCHAR(50),nickname VARCHAR(20),role VARCHAR(20),status VARCHAR(20),email_verified_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP,deleted_at TIMESTAMP,scheduled_delete_at TIMESTAMP)"
        );
        hash = encoder.encode("Soom1234!");
        jdbc.update(
            "INSERT INTO `user`(email,password,name,nickname,role,status,email_verified_at,created_at) VALUES (?,?,?,?, 'USER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
            "login@example.com",
            hash,
            "Tester",
            "tester"
        );
    }

    URI uri(String path) {
        return URI.create(
            "http://localhost:" + env.getProperty("local.server.port") + "/api/auth/" + path
        );
    }

    HttpResponse<String> get(String path) throws Exception {
        return client.send(
            HttpRequest.newBuilder(uri(path)).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    HttpResponse<String> post(String path, String body, boolean csrf) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        if (csrf) {
            String json = get("csrf").body();
            var matcher = java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"").matcher(json);
            assertTrue(matcher.find(), json);
            builder.header("X-XSRF-TOKEN", matcher.group(1));
        }
        return client.send(
            builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    String body =
        "{\"email\":\" LOGIN@example.com \",\"password\":\"Soom1234!\",\"rememberMe\":false}";

    HttpResponse<String> cookieGet(String token) throws Exception {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(uri("me"))
                .header("Cookie", "SOOM_ACCESS=" + token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void loginRestoresUserAndLogoutRevokesJwt() throws Exception {
        assertEquals(401, get("me").statusCode());
        var result = post("login", body, true);
        assertEquals(200, result.statusCode(), result.body());
        String cookie = result
            .headers()
            .allValues("set-cookie")
            .stream()
            .filter(c -> c.startsWith("SOOM_ACCESS="))
            .findFirst()
            .orElseThrow();
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        String token = cookie.substring("SOOM_ACCESS=".length()).split(";")[0];
        assertEquals(3, token.split("\\.").length);
        assertEquals("1", jwt.validate(token).getSubject());
        assertFalse(result.body().contains("password"));
        assertFalse(result.body().contains(hash));
        assertEquals(200, get("me").statusCode());
        assertTrue(get("me").body().contains("tester"));
        assertTrue(java.util.Set.of(401, 403).contains(post("logout", "{}", false).statusCode()));
        assertEquals(200, post("logout", "{}", true).statusCode());
        assertEquals(401, get("me").statusCode());
        assertEquals(401, cookieGet(token).statusCode());
    }

    @Test
    void rejectsWrongCredentialsAndMissingCsrf() throws Exception {
        assertTrue(java.util.Set.of(401, 403).contains(post("login", body, false).statusCode()));
        assertEquals(401, post("login", body.replace("Soom1234!", "Wrong123!"), true).statusCode());
        assertEquals(
            401,
            post(
                "login",
                body.replace("LOGIN@example.com", "nobody@example.com"),
                true
            ).statusCode()
        );
        assertEquals(400, post("login", "{}", true).statusCode());
    }

    @Test
    void blocksInactiveAndUnverifiedAccountsAndRechecksStatus() throws Exception {
        assertEquals(200, post("login", body, true).statusCode());
        jdbc.update("UPDATE `user` SET status='SUSPENDED'");
        assertEquals(401, get("me").statusCode());
        assertEquals(403, post("login", body, true).statusCode());
        jdbc.update("UPDATE `user` SET status='ACTIVE',deleted_at=CURRENT_TIMESTAMP");
        assertEquals(403, post("login", body, true).statusCode());
        jdbc.update("UPDATE `user` SET deleted_at=NULL,email_verified_at=NULL");
        assertEquals(403, post("login", body, true).statusCode());
    }

    @Test
    void rejectsInvalidExpiredTokensAndSupportsRememberMe() throws Exception {
        assertEquals(401, cookieGet("invalid.jwt.token").statusCode());
        String expiring = jwt.issue(1, 1);
        Thread.sleep(1100);
        assertEquals(401, cookieGet(expiring).statusCode());
        String token = jwt.issue(1, 60);
        int dot = token.lastIndexOf('.') + 1;
        String altered =
            token.substring(0, dot) +
            (token.charAt(dot) == 'A' ? 'B' : 'A') +
            token.substring(dot + 1);
        assertEquals(401, cookieGet(altered).statusCode());
        var result = post("login", body.replace("false", "true"), true);
        assertEquals(200, result.statusCode());
        assertTrue(
            result
                .headers()
                .allValues("set-cookie")
                .stream()
                .anyMatch(c -> c.contains("Max-Age=604800"))
        );
    }

    @Test
    void separatesUserAndAdminAndRechecksRole() throws Exception {
        URI admin = URI.create(
            "http://localhost:" + env.getProperty("local.server.port") + "/api/admin/access"
        );
        var request = HttpRequest.newBuilder(admin).GET().build();
        assertEquals(401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200, post("login", body, true).statusCode());
        assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        jdbc.update("UPDATE `user` SET role='ADMIN'");
        var allowed = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, allowed.statusCode());
        assertTrue(allowed.body().contains("ADMIN"));
        jdbc.update("UPDATE `user` SET role='USER'");
        assertEquals(403, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
