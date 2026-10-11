package com.example.demo.service;

import static org.springframework.http.HttpStatus.*;

import java.sql.Statement;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DashboardService {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final QuitRecordService records;

    public DashboardService(JdbcTemplate jdbc, QuitRecordService records) {
        this.jdbc = jdbc;
        this.records = records;
    }

    public record Milestone(
        long id,
        int minutes,
        String title,
        String label,
        int points,
        String description
    ) {}

    private static String milestoneLabel(int minutes) {
        return switch (minutes) {
            case 20 -> "20분 후";
            case 720 -> "8~12시간 후";
            case 1440 -> "24시간 후";
            case 2880 -> "48시간 · 2일 후";
            case 4320 -> "72시간 · 3일 후";
            case 20160 -> "2주~3개월";
            case 43200 -> "1~9개월";
            case 525600 -> "1년 후";
            case 2628000 -> "5년 이후";
            case 5256000 -> "10년 후";
            case 7884000 -> "15년 후";
            default -> minutes + "분 후";
        };
    }

    public record RecordDetail(
        long id,
        int growth,
        List<String> attendance,
        List<Integer> rewards
    ) {}

    public record Wish(long id, String name, int price, long recordId) {}

    public record Transaction(String label, int amount, String date) {}

    public record Notice(long id, String content, boolean read, String date) {}

    public record Snapshot(
        long points,
        List<Wish> wishes,
        List<Transaction> transactions,
        List<RecordDetail> recordDetails,
        List<Notice> notifications,
        long unreadCount
    ) {}

    public record Catalog(List<Milestone> milestones, List<String> quotes) {}

    static String requireText(String value, int max) {
        if (
            value == null || value.isBlank() || value.strip().length() > max
        ) throw new ResponseStatusException(BAD_REQUEST, "입력 내용과 길이를 확인해주세요.");
        return value.strip();
    }

    void lockUser(int userId) {
        jdbc.queryForObject(
            "SELECT user_id FROM `user` WHERE user_id=? FOR UPDATE",
            Integer.class,
            userId
        );
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                )
            );
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    boolean registerRequest(int userId, UUID requestId, String operation) {
        if (requestId == null) throw new ResponseStatusException(
            BAD_REQUEST,
            "요청 식별자가 필요합니다."
        );
        var existing = jdbc.queryForList(
            "SELECT payload_hash FROM dashboard_request WHERE user_id=? AND request_id=?",
            String.class,
            userId,
            requestId.toString()
        );
        if (!existing.isEmpty()) {
            if (!existing.get(0).equals(hash(operation))) throw new ResponseStatusException(
                CONFLICT,
                "같은 요청 식별자로 다른 내용을 저장할 수 없습니다."
            );
            return false;
        }
        jdbc.update(
            "INSERT INTO dashboard_request(user_id,request_id,payload_hash,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",
            userId,
            requestId.toString(),
            hash(operation)
        );
        return true;
    }

    long insertAndGetId(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var s = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            return s;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private QuitRecordService.View requireRecord(int userId, long id, boolean active) {
        var r = records
            .list(userId)
            .stream()
            .filter(x -> x.id() == id)
            .findFirst()
            .orElseThrow(() ->
                new ResponseStatusException(NOT_FOUND, "금연 기록을 찾을 수 없습니다.")
            );
        if (active && r.end() != null) throw new ResponseStatusException(
            CONFLICT,
            "진행 중인 금연 기록에서 이용해주세요."
        );
        return r;
    }

    long getBalance(int userId) {
        return jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount),0) FROM `point` WHERE user_id=?",
            Long.class,
            userId
        );
    }

    void sendNotification(int userId, String type, String content) {
        jdbc.update(
            "INSERT INTO notification(user_id,notification_type,content,is_read,created_at) VALUES(?,?,?,FALSE,CURRENT_TIMESTAMP)",
            userId,
            type,
            content
        );
    }

    private void recordPointChange(int userId, int amount, String type, String description) {
        jdbc.update(
            "INSERT INTO `point`(user_id,amount,point_type,description,created_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",
            userId,
            amount,
            type,
            description
        );
        jdbc.update(
            "INSERT INTO activity_record(user_id,activity_type,description,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",
            userId,
            type,
            description
        );
        sendNotification(
            userId,
            type,
            description + " (" + (amount > 0 ? "+" : "") + amount + "P)"
        );
    }

    public Catalog catalog() {
        var milestones = jdbc.query(
            "SELECT * FROM no_smoking_travel WHERE travel_order>0 ORDER BY travel_order,no_smoking_travel_id",
            (r, n) ->
                new Milestone(
                    r.getLong("no_smoking_travel_id"),
                    r.getInt("required_minutes"),
                    r.getString("title"),
                    milestoneLabel(r.getInt("required_minutes")),
                    r.getInt("reward_points"),
                    r.getString("description")
                )
        );
        return new Catalog(
            milestones,
            jdbc.queryForList(
                "SELECT content FROM fighting WHERE is_active=TRUE ORDER BY fighting_id",
                String.class
            )
        );
    }

    public Snapshot snapshot(int userId) {
        var details = records
            .list(userId)
            .stream()
            .map(r -> {
                var growth = jdbc.queryForList(
                    "SELECT growth_rate FROM no_smoking_tree WHERE no_smoking_record_id=?",
                    Integer.class,
                    r.id()
                );
                var attendance = jdbc.query(
                    "SELECT attendance_date FROM no_smoking_attendance WHERE no_smoking_record_id=? ORDER BY attendance_date",
                    (rs, n) -> rs.getDate(1).toString(),
                    r.id()
                );
                var rewards = jdbc.queryForList(
                    "SELECT t.required_minutes FROM no_smoking_travel_progress p JOIN no_smoking_travel t ON t.no_smoking_travel_id=p.no_smoking_travel_id WHERE p.no_smoking_record_id=?",
                    Integer.class,
                    r.id()
                );
                return new RecordDetail(
                    r.id(),
                    growth.isEmpty() ? 0 : growth.get(0),
                    attendance,
                    rewards
                );
            })
            .toList();
        var wishes = jdbc.query(
            "SELECT w.* FROM wishlist w JOIN no_smoking_record r ON r.no_smoking_record_id=w.no_smoking_record_id WHERE r.user_id=? ORDER BY w.wishlist_id DESC",
            (r, n) ->
                new Wish(
                    r.getLong("wishlist_id"),
                    r.getString("item_name"),
                    r.getInt("target_price"),
                    r.getLong("no_smoking_record_id")
                ),
            userId
        );
        var transactions = jdbc.query(
            "SELECT * FROM `point` WHERE user_id=? ORDER BY point_id DESC LIMIT 100",
            (r, n) ->
                new Transaction(
                    r.getString("description"),
                    r.getInt("amount"),
                    r.getTimestamp("created_at").toLocalDateTime().toLocalDate().toString()
                ),
            userId
        );
        var notices = jdbc.query(
            "SELECT * FROM notification WHERE user_id=? ORDER BY is_read ASC,notification_id DESC LIMIT 100",
            (r, n) ->
                new Notice(
                    r.getLong("notification_id"),
                    r.getString("content"),
                    r.getBoolean("is_read"),
                    r.getTimestamp("created_at").toLocalDateTime().toString()
                ),
            userId
        );
        return new Snapshot(
            getBalance(userId),
            wishes,
            transactions,
            details,
            notices,
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id=? AND is_read=FALSE",
                Long.class,
                userId
            )
        );
    }

    @Transactional
    public Snapshot sync(int userId) {
        lockUser(userId);
        long now = System.currentTimeMillis();
        var all = records.list(userId);
        var milestones = catalog().milestones();
        double savings = 0;
        for (var r : all) {
            long elapsed = Math.max(0, (r.end() == null ? now : r.end()) - r.start());
            savings += (((elapsed / 86400000.0) * r.daily()) / r.packSize()) * r.price();
            for (var m : milestones) {
                if (
                    elapsed / 60000 < m.minutes() ||
                    jdbc.queryForObject(
                        "SELECT COUNT(*) FROM no_smoking_travel_progress WHERE no_smoking_record_id=? AND no_smoking_travel_id=?",
                        Integer.class,
                        r.id(),
                        m.id()
                    ) > 0
                ) continue;
                jdbc.update(
                    "INSERT INTO no_smoking_travel_progress(no_smoking_record_id,no_smoking_travel_id,achieved_at,created_at) VALUES(?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    r.id(),
                    m.id()
                );
                if (m.points() > 0) recordPointChange(
                    userId,
                    m.points(),
                    "JOURNEY",
                    m.title() + " 달성"
                );
            }
        }
        for (var wish : snapshot(userId).wishes())
            jdbc.update(
                "UPDATE wishlist SET status=?,achieved_at=CASE WHEN ? THEN COALESCE(achieved_at,CURRENT_TIMESTAMP) ELSE NULL END,updated_at=CURRENT_TIMESTAMP WHERE wishlist_id=?",
                savings >= wish.price() ? "ACHIEVED" : "IN_PROGRESS",
                savings >= wish.price(),
                wish.id()
            );
        return snapshot(userId);
    }

    @Transactional
    public Snapshot attend(int userId, long id) {
        lockUser(userId);
        requireRecord(userId, id, true);
        LocalDate today = LocalDate.now(ZONE);
        if (
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM no_smoking_attendance WHERE no_smoking_record_id=? AND attendance_date=?",
                Integer.class,
                id,
                today
            ) > 0
        ) return snapshot(userId);
        jdbc.update(
            "INSERT INTO no_smoking_attendance(no_smoking_record_id,attendance_date,reward_points,created_at) VALUES(?,?,50,CURRENT_TIMESTAMP)",
            id,
            today
        );
        recordPointChange(userId, 50, "ATTENDANCE", "오늘의 출석");
        return snapshot(userId);
    }

    @Transactional
    public Snapshot nurture(int userId, long id, String kind, UUID requestId) {
        lockUser(userId);
        requireRecord(userId, id, true);
        if (!Set.of("water", "fertilizer").contains(kind)) throw new ResponseStatusException(
            BAD_REQUEST,
            "돌봄 종류를 확인해주세요."
        );
        if (!registerRequest(userId, requestId, "nurture:" + id + ":" + kind)) return snapshot(
            userId
        );
        var growths = jdbc.queryForList(
            "SELECT growth_rate FROM no_smoking_tree WHERE no_smoking_record_id=?",
            Integer.class,
            id
        );
        int growth = growths.isEmpty() ? 0 : growths.get(0),
            cost = kind.equals("water") ? 30 : 80,
            delta = kind.equals("water") ? 5 : 15;
        if (growth >= 100) throw new ResponseStatusException(CONFLICT, "이미 다 자란 나무예요.");
        if (getBalance(userId) < cost) throw new ResponseStatusException(
            CONFLICT,
            "포인트가 부족해요."
        );
        if (growths.isEmpty()) jdbc.update(
            "INSERT INTO no_smoking_tree(no_smoking_record_id,created_at) VALUES(?,CURRENT_TIMESTAMP)",
            id
        );
        int next = Math.min(100, growth + delta);
        jdbc.update(
            "UPDATE no_smoking_tree SET growth_rate=?,water_count=water_count+?,fertilizer_count=fertilizer_count+?,is_fully_grown=?,updated_at=CURRENT_TIMESTAMP WHERE no_smoking_record_id=?",
            next,
            kind.equals("water") ? 1 : 0,
            kind.equals("fertilizer") ? 1 : 0,
            next == 100,
            id
        );
        recordPointChange(
            userId,
            -cost,
            "TREE",
            kind.equals("water") ? "나무에 물 주기" : "나무에 비료 주기"
        );
        return snapshot(userId);
    }

    @Transactional
    public Snapshot saveWish(
        int userId,
        Long id,
        long recordId,
        String name,
        int price,
        UUID requestId
    ) {
        name = requireText(name, 100);
        if (price < 1 || price > 1000000000) throw new ResponseStatusException(
            BAD_REQUEST,
            "목표 금액을 확인해주세요."
        );
        lockUser(userId);
        requireRecord(userId, recordId, false);
        if (id == null) {
            if (
                registerRequest(userId, requestId, "wish:" + recordId + ":" + name + ":" + price)
            ) jdbc.update(
                "INSERT INTO wishlist(no_smoking_record_id,item_name,target_price,created_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",
                recordId,
                name,
                price
            );
            return sync(userId);
        }
        if (
            jdbc.update(
                "UPDATE wishlist SET item_name=?,target_price=?,updated_at=CURRENT_TIMESTAMP WHERE wishlist_id=? AND no_smoking_record_id IN (SELECT no_smoking_record_id FROM no_smoking_record WHERE user_id=?)",
                name,
                price,
                id,
                userId
            ) == 0
        ) throw new ResponseStatusException(NOT_FOUND, "목표를 찾을 수 없어요.");
        return sync(userId);
    }

    @Transactional
    public Snapshot deleteWish(int userId, long id) {
        lockUser(userId);
        if (
            jdbc.update(
                "DELETE FROM wishlist WHERE wishlist_id=? AND no_smoking_record_id IN (SELECT no_smoking_record_id FROM no_smoking_record WHERE user_id=?)",
                id,
                userId
            ) == 0
        ) throw new ResponseStatusException(NOT_FOUND, "목표를 찾을 수 없어요.");
        return snapshot(userId);
    }

    @Transactional
    public Snapshot deleteNotice(int userId, long id) {
        lockUser(userId);
        jdbc.update("DELETE FROM notification WHERE notification_id=? AND user_id=?", id, userId);
        return snapshot(userId);
    }

    @Transactional
    public Snapshot deleteAllNotices(int userId) {
        lockUser(userId);
        jdbc.update("DELETE FROM notification WHERE user_id=?", userId);
        return snapshot(userId);
    }

    @Transactional
    public Snapshot readNotice(int userId, long id) {
        lockUser(userId);
        if (
            jdbc.update(
                "UPDATE notification SET is_read=TRUE,read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE notification_id=? AND user_id=?",
                id,
                userId
            ) == 0
        ) throw new ResponseStatusException(NOT_FOUND, "알림을 찾을 수 없어요.");
        return snapshot(userId);
    }
}
