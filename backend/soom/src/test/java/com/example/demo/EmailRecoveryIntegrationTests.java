package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.demo.service.EmailVerificationService;
import java.net.*;
import java.net.http.*;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:emailrecovery;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
class EmailRecoveryIntegrationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Environment environment;

    @MockitoBean
    EmailVerificationService verification;

    HttpClient client;
    String answers = "[\"파랑\",\"피자\",\"제주도\",\"The Hobbit\",\"의사\"]";

    @BeforeEach
    void setup() {
        client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
        jdbc.execute("DELETE FROM email_recovery_hint");
        jdbc.execute("DELETE FROM email_recovery_attempt");
        jdbc.execute("DROP TABLE IF EXISTS `user`");
        jdbc.execute(
            "CREATE TABLE `user` (user_id INT AUTO_INCREMENT PRIMARY KEY,email VARCHAR(100) UNIQUE,password VARCHAR(255),name VARCHAR(50),nickname VARCHAR(20) UNIQUE,role VARCHAR(20),status VARCHAR(20),email_verified_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP,deleted_at TIMESTAMP,scheduled_delete_at TIMESTAMP)"
        );
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        })
            .when(verification)
            .withVerifiedEmail(anyString(), anyString(), any());
    }

    URI uri(String path) {
        return URI.create(
            "http://localhost:" + environment.getProperty("local.server.port") + "/api/auth/" + path
        );
    }

    HttpResponse<String> post(String path, String body) throws Exception {
        var csrfResponse = client.send(
            HttpRequest.newBuilder(uri("csrf")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        var csrf = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(csrfResponse.body());
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

    String signupBody(String email, String nickname, String suppliedAnswers) {
        return (
            "{\"email\":\"" +
            email +
            "\",\"password\":\"Soom1234!\",\"name\":\"Tester\",\"nickname\":\"" +
            nickname +
            "\",\"verificationToken\":\"proof\",\"recoveryAnswers\":" +
            suppliedAnswers +
            "}"
        );
    }

    String findBody(String nickname, String suppliedAnswers) {
        return "{\"nickname\":\"" + nickname + "\",\"recoveryAnswers\":" + suppliedAnswers + "}";
    }

    void signup() throws Exception {
        var created = post("signup", signupBody("lolplayer@naver.com", "tester", answers));
        assertEquals(201, created.statusCode(), created.body());
    }

    @Test
    void signupStoresFiveSaltedHashesAndFindsOnlyMaskedEmail() throws Exception {
        signup();
        assertEquals(
            201,
            post("signup", signupBody("second@example.com", "second", answers)).statusCode()
        );
        List<String> hashes = jdbc.queryForList(
            "SELECT answer_hash FROM email_recovery_hint ORDER BY user_id,question_number",
            String.class
        );
        assertEquals(10, hashes.size());
        assertTrue(hashes.stream().allMatch(hash -> hash.startsWith("$2")));
        assertNotEquals(hashes.get(0), hashes.get(5));
        var response = post("find-id", findBody("tester", answers));
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("lol****@naver.com"));
        assertFalse(response.body().contains("lolplayer"));
        assertFalse(response.body().contains("answer"));
        assertFalse(response.body().contains(hashes.get(0)));
    }

    @Test
    void allFiveAnswersMustMatchInOrder() throws Exception {
        signup();
        for (String answer : List.of("파랑", "피자", "제주도", "The Hobbit", "의사")) {
            var result = post("find-id", findBody("tester", answers.replace(answer, "wrong")));
            assertEquals(400, result.statusCode(), result.body());
            assertFalse(result.body().contains("naver.com"));
        }
        assertEquals(429, post("find-id", findBody("tester", answers)).statusCode());
        assertEquals(
            5,
            jdbc.queryForObject("SELECT attempts FROM email_recovery_attempt", Integer.class)
        );
        jdbc.update(
            "UPDATE email_recovery_attempt SET window_started_at=DATEADD('MINUTE',-16,CURRENT_TIMESTAMP)"
        );
        assertEquals(200, post("find-id", findBody("tester", answers)).statusCode());
    }

    @Test
    void normalizesAnswersAndNeverRevealsAnEntireShortEmail() throws Exception {
        var created = post(
            "signup",
            signupBody("a@example.com", "tester", answers.replace("The Hobbit", "  THE   HOBBIT  "))
        );
        assertEquals(201, created.statusCode(), created.body());
        var found = post("find-id", findBody("tester", answers));
        assertEquals(200, found.statusCode(), found.body());
        assertTrue(found.body().contains("****@example.com"));
        assertFalse(found.body().contains("a@example.com"));
    }

    @Test
    void requiresFiveSlotsAndAtLeastOneAnswerBeforeSignupOrLookup() throws Exception {
        for (String invalid : List.of(
            "null",
            "[]",
            "[\"one\",\"two\",\"three\",\"four\"]",
            "[\"\",\" \",\"\",\"\",\"\"]",
            "[null,\"a\",\"\",\"\",\"\"]",
            answers.replace("의사\"]", "의사\",\"extra\"]"),
            answers.replace("파랑", "x".repeat(41))
        )) {
            assertEquals(
                400,
                post("signup", signupBody("invalid@example.com", "tester", invalid)).statusCode()
            );
            assertEquals(400, post("find-id", findBody("tester", invalid)).statusCode());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
        assertEquals(
            0,
            jdbc.queryForObject("SELECT COUNT(*) FROM email_recovery_hint", Integer.class)
        );
    }

    @Test
    void missingHintsUnknownNicknameAndInactiveUserNeverRevealEmail() throws Exception {
        signup();
        assertEquals(400, post("find-id", findBody("unknown", answers)).statusCode());
        jdbc.update("UPDATE `user` SET status='SUSPENDED'");
        assertEquals(400, post("find-id", findBody("tester", answers)).statusCode());
        jdbc.update("UPDATE `user` SET status='ACTIVE'");
        jdbc.update("DELETE FROM email_recovery_hint");
        assertEquals(400, post("find-id", findBody("tester", answers)).statusCode());
    }

    @Test
    void signupAndHintsRollbackTogether() throws Exception {
        jdbc.update(
            "INSERT INTO email_recovery_hint(user_id,question_number,answer_hash) VALUES (1,1,'existing')"
        );
        assertEquals(
            409,
            post("signup", signupBody("rollback@example.com", "tester", answers)).statusCode()
        );
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM `user`", Integer.class));
        assertEquals(
            1,
            jdbc.queryForObject("SELECT COUNT(*) FROM email_recovery_hint", Integer.class)
        );
    }

    @Test
    void selectedQuestionsRetainTheirNumbersAndRequireEveryRegisteredAnswer() throws Exception {
        String selected = "[\"\",\"피자\",\"\",\"The Hobbit\",\"\"]";
        assertEquals(
            201,
            post("signup", signupBody("selected@example.com", "tester", selected)).statusCode()
        );
        assertEquals(
            List.of(2, 4),
            jdbc.queryForList(
                "SELECT question_number FROM email_recovery_hint ORDER BY question_number",
                Integer.class
            )
        );
        var questions = post("find-id/questions", "{\"nickname\":\"tester\"}");
        assertEquals(200, questions.statusCode(), questions.body());
        assertEquals("{\"questionNumbers\":[2,4]}", questions.body());
        assertEquals(
            400,
            post("find-id", findBody("tester", selected.replace("피자", ""))).statusCode()
        );
        assertEquals(400, post("find-id", findBody("tester", answers)).statusCode());
        assertEquals(200, post("find-id", findBody("tester", selected)).statusCode());
    }

    @Test
    void eachSingleQuestionCanRecoverAnAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            var slots = new java.util.ArrayList<String>(java.util.Collections.nCopies(5, ""));
            slots.set(i, "answer");
            String supplied =
                "[" +
                slots
                    .stream()
                    .map(a -> "\"" + a + "\"")
                    .collect(java.util.stream.Collectors.joining(",")) +
                "]";
            String nickname = "tester" + i;
            assertEquals(
                201,
                post(
                    "signup",
                    signupBody(nickname + "@example.com", nickname, supplied)
                ).statusCode()
            );
            assertEquals(200, post("find-id", findBody(nickname, supplied)).statusCode());
        }
    }

    @Test
    void questionsDoNotExposeAnswersAndAreRateLimited() throws Exception {
        signup();
        for (int i = 0; i < 5; i++) {
            var result = post("find-id/questions", "{\"nickname\":\"tester\"}");
            assertEquals(200, result.statusCode());
            assertEquals("{\"questionNumbers\":[1,2,3,4,5]}", result.body());
        }
        assertEquals(429, post("find-id/questions", "{\"nickname\":\"tester\"}").statusCode());
        assertEquals(400, post("find-id/questions", "{\"nickname\":\"unknown\"}").statusCode());
        jdbc.update("UPDATE `user` SET status='SUSPENDED'");
        jdbc.update("DELETE FROM email_recovery_attempt");
        assertEquals(400, post("find-id/questions", "{\"nickname\":\"tester\"}").statusCode());
    }

    @Test
    void lookupRequiresCsrf() throws Exception {
        var result = client.send(
            HttpRequest.newBuilder(uri("find-id"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(findBody("tester", answers)))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(403, result.statusCode());
    }
}
