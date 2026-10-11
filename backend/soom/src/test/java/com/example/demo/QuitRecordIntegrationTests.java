package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.example.demo.service.JwtService;
import java.net.*;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:records;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
class QuitRecordIntegrationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment env;

    @Autowired
    JwtService jwt;

    HttpClient client;
    String token;

    @BeforeEach
    void setup() {
        client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
        jdbc.execute("DROP TABLE IF EXISTS no_smoking_record");
        jdbc.execute("DROP TABLE IF EXISTS `user`");
        jdbc.execute("DELETE FROM quit_record_import");
        jdbc.execute(
            "CREATE TABLE `user` (user_id INT PRIMARY KEY,email VARCHAR(100),password VARCHAR(255),name VARCHAR(50),nickname VARCHAR(20),role VARCHAR(20),status VARCHAR(20),email_verified_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP,deleted_at TIMESTAMP,scheduled_delete_at TIMESTAMP)"
        );
        jdbc.execute(
            "INSERT INTO `user` (user_id,email,name,nickname,role,status,email_verified_at,created_at) VALUES (1,'one@test.com','One','one','USER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'two@test.com','Two','two','USER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"
        );
        jdbc.execute(
            "CREATE TABLE no_smoking_record (no_smoking_record_id INT AUTO_INCREMENT PRIMARY KEY,user_id INT NOT NULL,start_date TIMESTAMP NOT NULL,end_date TIMESTAMP,status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',daily_cigarettes INT NOT NULL,cigarette_price INT NOT NULL,cigarettes_per_pack INT NOT NULL DEFAULT 20,created_at TIMESTAMP NOT NULL,updated_at TIMESTAMP)"
        );
        jdbc.execute("UPDATE `user` SET password='test-credential-hash'");
        for (String table : java.util.List.of(
            "wishlist",
            "no_smoking_tree",
            "no_smoking_attendance",
            "no_smoking_travel_progress"
        ))
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (no_smoking_record_id INT)");
        token = jwt.issue(1, 3600);
    }

    URI uri(String path) {
        return URI.create("http://localhost:" + env.getProperty("local.server.port") + path);
    }

    HttpResponse<String> call(String path, String body, boolean csrf) throws Exception {
        var b = HttpRequest.newBuilder(uri(path));
        if (token != null) b.header("Cookie", "SOOM_ACCESS=" + token);
        if (body != null) {
            b.header("Content-Type", "application/json");
            if (csrf) {
                var json = client
                    .send(
                        HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                    )
                    .body();
                var m = java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"").matcher(json);
                assertTrue(m.find(), json);
                b.header("X-XSRF-TOKEN", m.group(1));
            }
            b.POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    String input(String key, boolean ended) {
        long start = System.currentTimeMillis() - 86400000;
        return (
            "{\"start\":" +
            start +
            ",\"end\":" +
            (ended ? Long.toString(start + 3600000) : "null") +
            ",\"daily\":10,\"price\":5000,\"packSize\":20,\"clientId\":\"" +
            key +
            "\"}"
        );
    }

    long id() {
        return jdbc.queryForObject(
            "SELECT MAX(no_smoking_record_id) FROM no_smoking_record",
            Long.class
        );
    }

    @Test
    void guestAndCsrfAreEnforced() throws Exception {
        token = null;
        assertEquals(401, call("/api/quit-records", null, false).statusCode());
        assertEquals(
            401,
            call("/api/quit-records", input(UUID.randomUUID().toString(), false), true).statusCode()
        );
        for (String table : java.util.List.of(
            "wishlist",
            "no_smoking_tree",
            "no_smoking_attendance",
            "no_smoking_travel_progress"
        ))
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (no_smoking_record_id INT)");
        token = jwt.issue(1, 3600);
        assertEquals(
            403,
            call(
                "/api/quit-records",
                input(UUID.randomUUID().toString(), false),
                false
            ).statusCode()
        );
    }

    @Test
    void createReadUpdateDeleteAndOwnerIsolation() throws Exception {
        String key = UUID.randomUUID().toString(),
            body = input(key, false);
        assertEquals(201, call("/api/quit-records", body, true).statusCode());
        long id = id();
        assertTrue(call("/api/quit-records", null, false).body().contains("\"price\":5000"));
        token = jwt.issue(2, 3600);
        assertEquals("[]", call("/api/quit-records", null, false).body());
        assertEquals(404, call("/api/quit-records/" + id + "/update", body, true).statusCode());
        assertEquals(404, call("/api/quit-records/" + id + "/delete", "{}", true).statusCode());
        for (String table : java.util.List.of(
            "wishlist",
            "no_smoking_tree",
            "no_smoking_attendance",
            "no_smoking_travel_progress"
        ))
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (no_smoking_record_id INT)");
        token = jwt.issue(1, 3600);
        assertEquals(
            200,
            call("/api/quit-records/" + id + "/update", input(key, true), true).statusCode()
        );
        assertNotNull(
            jdbc.queryForObject("SELECT end_date FROM no_smoking_record", java.sql.Timestamp.class)
        );
        assertEquals(200, call("/api/quit-records/" + id + "/delete", "{}", true).statusCode());
        assertEquals("[]", call("/api/quit-records", null, false).body());
    }

    @Test
    void retriesAndDeletedImportsAreIdempotent() throws Exception {
        String body = input(UUID.randomUUID().toString(), false);
        var first = call("/api/quit-records/import", body, true);
        assertEquals(200, first.statusCode(), first.body());
        assertEquals(first.body(), call("/api/quit-records/import", body, true).body());
        assertEquals(
            409,
            call(
                "/api/quit-records/import",
                body.replace("\"daily\":10", "\"daily\":11"),
                true
            ).statusCode()
        );
        assertEquals(
            1,
            jdbc.queryForObject("SELECT COUNT(*) FROM no_smoking_record", Integer.class)
        );
        call("/api/quit-records/" + id() + "/delete", "{}", true);
        assertEquals(200, call("/api/quit-records/import", body, true).statusCode());
        assertEquals(
            0,
            jdbc.queryForObject("SELECT COUNT(*) FROM no_smoking_record", Integer.class)
        );
    }

    @Test
    void rejectsConflictingActiveAndInvalidInputs() throws Exception {
        assertEquals(
            201,
            call("/api/quit-records", input(UUID.randomUUID().toString(), false), true).statusCode()
        );
        assertEquals(
            409,
            call(
                "/api/quit-records/import",
                input(UUID.randomUUID().toString(), false),
                true
            ).statusCode()
        );
        assertEquals(
            201,
            call("/api/quit-records", input(UUID.randomUUID().toString(), true), true).statusCode()
        );
        assertEquals(
            400,
            call(
                "/api/quit-records",
                input(UUID.randomUUID().toString(), true).replace(
                    "\"packSize\":20",
                    "\"packSize\":0"
                ),
                true
            ).statusCode()
        );
        assertEquals(400, call("/api/quit-records", "{}", true).statusCode());
    }

    @Test
    void ignoresClientUserIdAndSeparatesImportKeysByAccount() throws Exception {
        String body = input(UUID.randomUUID().toString(), true).replace("{", "{\"userId\":2,");
        assertEquals(201, call("/api/quit-records", body, true).statusCode());
        assertEquals(
            1,
            jdbc.queryForObject("SELECT user_id FROM no_smoking_record", Integer.class)
        );
        token = jwt.issue(2, 3600);
        assertEquals(200, call("/api/quit-records/import", body, true).statusCode());
        assertEquals(
            2,
            jdbc.queryForObject("SELECT COUNT(*) FROM no_smoking_record", Integer.class)
        );
    }
}
