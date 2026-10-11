package com.example.demo;

import static org.junit.jupiter.api.Assertions.*;

import com.example.demo.service.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:dashboard;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
    }
)
class DashboardIntegrationTests {

    @Autowired
    JdbcTemplate db;

    @Autowired
    DataSource source;

    @Autowired
    Environment env;

    @Autowired
    JwtService jwt;

    @Autowired
    DashboardService dashboard;

    @Autowired
    CommunityService community;

    HttpClient client;
    String token;

    @BeforeEach
    void setup() throws Exception {
        try (var c = source.getConnection()) {
            assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:dashboard"));
        }
        db.execute("DROP ALL OBJECTS");
        var schema = new ResourceDatabasePopulator(
            new ClassPathResource("dashboard-test-schema.sql"),
            new ClassPathResource("dashboard-schema.sql"),
            new ClassPathResource("quit-record-schema.sql"),
            new ClassPathResource("dashboard-catalog.sql")
        );
        schema.setSqlScriptEncoding("UTF-8");
        schema.execute(source);
        db.execute(
            "INSERT INTO `user`(user_id,email,password,name,nickname,role,status,email_verified_at,created_at) VALUES(1,'one@test.invalid','test-hash','One','one','USER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),(2,'two@test.invalid','test-hash','Two','two','USER','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)"
        );
        db.update(
            "INSERT INTO no_smoking_record(user_id,start_date,daily_cigarettes,cigarette_price,cigarettes_per_pack,created_at) VALUES(1,?,10,5000,20,CURRENT_TIMESTAMP)",
            java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusMinutes(30)
        );
        client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
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
                var result = client.send(
                    HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
                );
                var m = java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"").matcher(
                    result.body()
                );
                assertTrue(m.find());
                b.header("X-XSRF-TOKEN", m.group(1));
            }
            b.POST(HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    String ok(String path, String body) throws Exception {
        var r = call(path, body, true);
        assertEquals(200, r.statusCode(), r.body());
        return r.body();
    }

    int count(String table) {
        return db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    void protectsPersonalApisAndAllowsPublicReading() throws Exception {
        token = null;
        assertEquals(401, call("/api/dashboard", null, false).statusCode());
        assertEquals(
            401,
            call(
                "/api/dashboard/tree",
                "{\"recordId\":1,\"kind\":\"water\",\"requestId\":\"" + key() + "\"}",
                true
            ).statusCode()
        );
        assertEquals(200, call("/api/community", null, false).statusCode());
        assertTrue(ok("/api/community/catalog", null).contains("혈압과 맥박의 변화"));
        token = jwt.issue(1, 3600);
        assertEquals(
            403,
            call("/api/dashboard/attendance", "{\"recordId\":1}", false).statusCode()
        );
        assertEquals(400, call("/api/dashboard/tree", "{}", true).statusCode());
    }

    @Test
    void grantsRewardsOnceAndSerializesConcurrentAttendance() throws Exception {
        assertTrue(ok("/api/dashboard/sync", "{}").contains("\"points\":20"));
        ok("/api/dashboard/sync", "{}");
        assertEquals(1, count("point"));
        var executor = Executors.newFixedThreadPool(6);
        try {
            var tasks = new ArrayList<Callable<DashboardService.Snapshot>>();
            for (int i = 0; i < 6; i++) tasks.add(() -> dashboard.attend(1, 1));
            for (var f : executor.invokeAll(tasks)) assertEquals(70, f.get().points());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, count("no_smoking_attendance"));
        assertEquals(2, count("point"));
        String request = "{\"recordId\":1,\"kind\":\"water\",\"requestId\":\"" + key() + "\"}";
        assertTrue(ok("/api/dashboard/tree", request).contains("\"growth\":5"));
        ok("/api/dashboard/tree", request);
        assertEquals(40, dashboard.snapshot(1).points());
        assertEquals(
            1,
            db.queryForObject("SELECT water_count FROM no_smoking_tree", Integer.class)
        );
        int receipts = count("dashboard_request");
        assertEquals(
            409,
            call(
                "/api/dashboard/tree",
                request
                    .replace("water", "fertilizer")
                    .replaceAll("[a-f0-9]{8}-[a-f0-9-]{27}", key()),
                true
            ).statusCode()
        );
        assertEquals(receipts, count("dashboard_request"));
    }

    @Test
    void preventsCrossAccountMutationAndCleansRecordRelations() throws Exception {
        ok("/api/dashboard/sync", "{}");
        ok("/api/dashboard/attendance", "{\"recordId\":1}");
        String wish =
            "{\"recordId\":1,\"name\":\"운동화\",\"price\":10000,\"requestId\":\"" + key() + "\"}";
        ok("/api/dashboard/wishes", wish);
        ok("/api/dashboard/wishes", wish);
        assertEquals(1, count("wishlist"));
        token = jwt.issue(2, 3600);
        assertTrue(ok("/api/dashboard", null).contains("\"wishes\":[]"));
        assertEquals(404, call("/api/dashboard/attendance", "{\"recordId\":1}", true).statusCode());
        assertEquals(404, call("/api/dashboard/wishes/1/delete", "{}", true).statusCode());
        assertEquals(404, call("/api/dashboard/notifications/1/read", "{}", true).statusCode());
        token = jwt.issue(1, 3600);
        ok("/api/dashboard/notifications/1/read", "{}");
        assertTrue(
            db.queryForObject(
                "SELECT is_read FROM notification WHERE notification_id=1",
                Boolean.class
            )
        );
        ok("/api/quit-records/1/delete", "{}");
        assertEquals(0, count("wishlist"));
        assertEquals(0, count("no_smoking_attendance"));
        assertEquals(0, count("no_smoking_travel_progress"));
        assertEquals(70, dashboard.snapshot(1).points());
    }

    String postBody(String image) {
        return (
            "{\"title\":\"오늘의 기록\",\"content\":\"함께 금연해요\",\"category\":\"금연 일기\",\"images\":[" +
            (image == null ? "" : "\"" + image + "\"") +
            "],\"requestId\":\"" +
            key() +
            "\"}"
        );
    }

    @Test
    void persistsCommunityImagesLikesCommentsReportsAndOwnership() throws Exception {
        String image =
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jC9sAAAAASUVORK5CYII=";
        String body = postBody(image);
        String feed = ok("/api/community/posts", body);
        ok("/api/community/posts", body);
        assertEquals(1, count("post"));
        assertTrue(feed.contains("/api/community/images/1"));
        assertEquals(200, call("/api/community/images/1", null, false).statusCode());
        token = jwt.issue(2, 3600);
        assertEquals(
            404,
            call(
                "/api/community/posts",
                postBody(null).replace("{", "{\"id\":1,"),
                true
            ).statusCode()
        );
        ok("/api/community/1/like", "{\"liked\":true}");
        ok("/api/community/1/like", "{\"liked\":true}");
        assertEquals(1, count("post_like"));
        String comment = "{\"content\":\"응원해요\",\"requestId\":\"" + key() + "\"}";
        ok("/api/community/1/comments", comment);
        ok("/api/community/1/comments", comment);
        assertEquals(1, count("comment"));
        assertEquals(1, count("notification"));
        String report = "{\"type\":\"POST\",\"id\":1,\"reason\":\"검토 요청\"}";
        ok("/api/community/reports", report);
        ok("/api/community/reports", report);
        assertEquals(1, count("report"));
        token = jwt.issue(1, 3600);
        assertEquals(
            404,
            call(
                "/api/community/comments/1/update",
                "{\"content\":\"다른 사람 댓글\"}",
                true
            ).statusCode()
        );
        ok(
            "/api/community/1/comments",
            "{\"parent\":1,\"content\":\"감사합니다\",\"requestId\":\"" + key() + "\"}"
        );
        token = jwt.issue(2, 3600);
        assertTrue(ok("/api/community/comments/1/delete", "{}").contains("\"comments\":[]"));
        assertEquals(0, count("comment"));
        token = null;
        ok("/api/community/1/view", "{}");
        assertEquals(1, db.queryForObject("SELECT view_count FROM post", Integer.class));
        token = jwt.issue(1, 3600);
        ok("/api/community/1/delete", "{}");
        assertEquals(404, call("/api/community/images/1", null, false).statusCode());
        assertTrue(ok("/api/community", null).contains("\"posts\":[]"));
    }

    @Test
    void unreadCountAndDeletionCoverAllNoticesAndStayAccountScoped() throws Exception {
        for (int i = 0; i < 105; i++) db.update(
            "INSERT INTO notification(user_id,notification_type,content,is_read,created_at) VALUES(1,'TEST','notice',FALSE,CURRENT_TIMESTAMP)"
        );
        db.update(
            "INSERT INTO notification(user_id,notification_type,content,is_read,created_at) VALUES(2,'TEST','other account',FALSE,CURRENT_TIMESTAMP)"
        );
        assertEquals(105, dashboard.snapshot(1).unreadCount());
        assertEquals(100, dashboard.snapshot(1).notifications().size());
        ok("/api/dashboard/notifications/1/read", "{}");
        assertEquals(104, dashboard.snapshot(1).unreadCount());
        assertFalse(dashboard.snapshot(1).notifications().get(0).read());
        token = jwt.issue(2, 3600);
        ok("/api/dashboard/notifications/2/delete", "{}");
        assertEquals(
            105,
            db.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id=1", Integer.class)
        );
        token = jwt.issue(1, 3600);
        assertEquals(
            403,
            call("/api/dashboard/notifications/delete-all", "{}", false).statusCode()
        );
        ok("/api/dashboard/notifications/2/delete", "{}");
        ok("/api/dashboard/notifications/2/delete", "{}");
        assertEquals(103, dashboard.snapshot(1).unreadCount());
        ok("/api/dashboard/notifications/delete-all", "{}");
        ok("/api/dashboard/notifications/delete-all", "{}");
        assertEquals(0, dashboard.snapshot(1).unreadCount());
        assertEquals(0, dashboard.snapshot(1).notifications().size());
        assertEquals(1, dashboard.snapshot(2).unreadCount());
        token = null;
        assertEquals(401, call("/api/dashboard/notifications/delete-all", "{}", true).statusCode());
        assertEquals(401, call("/api/dashboard/notifications/106/delete", "{}", true).statusCode());
    }

    @Test
    void healthCatalogPreservesExistingRewardsWhenAppliedAgain() throws Exception {
        ok("/api/dashboard/sync", "{}");
        long first = dashboard.catalog().milestones().get(0).id();
        var script = new ResourceDatabasePopulator(new ClassPathResource("dashboard-catalog.sql"));
        script.setSqlScriptEncoding("UTF-8");
        script.execute(source);
        var milestones = dashboard.catalog().milestones();
        assertEquals(11, milestones.size());
        assertEquals(first, milestones.get(0).id());
        assertEquals(7884000, milestones.get(10).minutes());
        assertTrue(milestones.stream().allMatch(m -> !m.description().isBlank()));
        assertFalse(
            milestones.stream().anyMatch(m -> m.minutes() == 10080 || m.minutes() == 129600)
        );
        ok("/api/dashboard/sync", "{}");
        assertEquals(1, count("point"));
        assertEquals(20, dashboard.snapshot(1).points());
    }

    @Test
    void wishStatusTracksSavingsImmediatelyAfterSave() throws Exception {
        String request =
            "{\"recordId\":1,\"name\":\"목표\",\"price\":1,\"requestId\":\"" + key() + "\"}";
        ok("/api/dashboard/wishes", request);
        assertEquals("ACHIEVED", db.queryForObject("SELECT status FROM wishlist", String.class));
        assertNotNull(
            db.queryForObject("SELECT achieved_at FROM wishlist", java.sql.Timestamp.class)
        );
        ok(
            "/api/dashboard/wishes",
            request.replace("{", "{\"id\":1,").replace("\"price\":1,", "\"price\":1000000,")
        );
        assertEquals("IN_PROGRESS", db.queryForObject("SELECT status FROM wishlist", String.class));
        assertNull(db.queryForObject("SELECT achieved_at FROM wishlist", java.sql.Timestamp.class));
    }

    @Test
    void migratesOldNestedRepliesWithoutLosingTheirTargets() {
        db.update(
            "INSERT INTO post(post_id,user_id,title,content,created_at) VALUES(1,1,'test','test',CURRENT_TIMESTAMP)"
        );
        for (int i = 1; i <= 4; i++) db.update(
            "INSERT INTO `comment`(comment_id,post_id,user_id,parent_comment_id,content,created_at) VALUES(?,1,1,?,'test',CURRENT_TIMESTAMP)",
            i,
            i == 1 ? null : i - 1
        );
        CommentThreadMigration.flattenExisting(db);
        CommentThreadMigration.flattenExisting(db);
        assertEquals(
            3,
            db.queryForObject(
                "SELECT COUNT(*) FROM `comment` WHERE parent_comment_id=1",
                Integer.class
            )
        );
        assertEquals(
            3,
            db.queryForObject(
                "SELECT reply_to_id FROM comment_reply_target WHERE comment_id=4",
                Integer.class
            )
        );
        assertEquals(3, count("comment_reply_target"));
    }

    @Test
    void supportsDeepRepliesAndDeletesDescendants() throws Exception {
        ok("/api/community/posts", postBody(null));
        Long parent = null;
        for (int i = 0; i < 12; i++) {
            ok(
                "/api/community/1/comments",
                "{\"parent\":" +
                    parent +
                    ",\"content\":\"level " +
                    i +
                    "\",\"requestId\":\"" +
                    key() +
                    "\"}"
            );
            parent = db.queryForObject("SELECT MAX(comment_id) FROM `comment`", Long.class);
        }
        assertEquals(12, count("comment"));
        assertEquals(
            1,
            db.queryForObject(
                "SELECT parent_comment_id FROM `comment` WHERE comment_id=12",
                Integer.class
            )
        );
        assertEquals(
            11,
            db.queryForObject(
                "SELECT reply_to_id FROM comment_reply_target WHERE comment_id=12",
                Integer.class
            )
        );
        String feed = ok("/api/community/comments/2/delete", "{}");
        assertFalse(feed.contains("\"deleted\":true"));
        assertFalse(feed.contains("level 11"));
        assertEquals(1, count("comment"));
        assertEquals(0, count("comment_reply_target"));
        assertEquals(
            400,
            call(
                "/api/community/1/comments",
                "{\"parent\":2,\"content\":\"deleted parent\",\"requestId\":\"" + key() + "\"}",
                true
            ).statusCode()
        );
    }

    @Test
    void editsAreMarkedAndDeletingReplyPreservesSibling() throws Exception {
        community.save(1, null, "title", "body", "금연 일기", List.of(), UUID.randomUUID());
        assertFalse(community.feed(1).posts().get(0).edited());
        community.view(1, 1);
        assertFalse(community.feed(1).posts().get(0).edited());
        community.save(1, 1L, "edited title", "body", "금연 일기", List.of(), UUID.randomUUID());
        assertTrue(community.feed(null).posts().get(0).edited());
        community.comment(1, 1, null, "root", UUID.randomUUID());
        community.comment(2, 1, 1L, "child", UUID.randomUUID());
        community.comment(1, 1, 2L, "grandchild", UUID.randomUUID());
        community.comment(1, 1, 1L, "sibling", UUID.randomUUID());
        assertFalse(community.feed(1).posts().get(0).comments().get(1).edited());
        community.editComment(2, 2, "edited reply", false);
        assertTrue(community.feed(null).posts().get(0).comments().get(1).edited());
        assertEquals(404, call("/api/community/comments/2/delete", "{}", true).statusCode());
        assertEquals(4, count("comment"));
        community.editComment(2, 2, null, true);
        assertEquals(
            List.of(1L, 4L),
            community
                .feed(null)
                .posts()
                .get(0)
                .comments()
                .stream()
                .map(CommunityService.Comment::id)
                .toList()
        );
        assertEquals(2, count("comment"));
        assertEquals(1, count("comment_reply_target"));
    }

    @Test
    void invalidImagesAndCrossPostRepliesRollback() throws Exception {
        assertEquals(
            400,
            call(
                "/api/community/posts",
                postBody("data:image/png;base64,AAAAAAAAAAAAAAAAAAAA"),
                true
            ).statusCode()
        );
        assertEquals(0, count("post"));
        assertEquals(0, count("dashboard_request"));
        ok("/api/community/posts", postBody(null));
        ok("/api/community/posts", postBody(null));
        ok("/api/community/1/comments", "{\"content\":\"first\",\"requestId\":\"" + key() + "\"}");
        assertEquals(
            400,
            call(
                "/api/community/2/comments",
                "{\"parent\":1,\"content\":\"wrong parent\",\"requestId\":\"" + key() + "\"}",
                true
            ).statusCode()
        );
        assertEquals(1, count("comment"));
    }
}
