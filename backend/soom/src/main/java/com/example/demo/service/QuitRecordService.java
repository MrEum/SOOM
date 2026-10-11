package com.example.demo.service;

import static org.springframework.http.HttpStatus.*;

import jakarta.validation.constraints.*;
import java.sql.Statement;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuitRecordService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;

    public QuitRecordService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Input(
        @NotNull @Positive Long start,
        Long end,
        @Min(1) @Max(200) int daily,
        @Min(1) @Max(100000) int price,
        @Min(1) @Max(200) int packSize,
        @NotNull UUID clientId
    ) {}

    public record View(long id, long start, Long end, int daily, int price, int packSize) {}

    private LocalDateTime local(long epoch) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZONE);
    }

    private long epoch(LocalDateTime date) {
        return date.atZone(ZONE).toInstant().toEpochMilli();
    }

    private String fingerprint(Input input) {
        try {
            String payload =
                input.start() +
                ":" +
                input.end() +
                ":" +
                input.daily() +
                ":" +
                input.price() +
                ":" +
                input.packSize();
            return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(
                    payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                )
            );
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<View> list(int userId) {
        return jdbc.query(
            "SELECT * FROM no_smoking_record WHERE user_id=? ORDER BY start_date DESC, no_smoking_record_id DESC",
            (rs, n) -> {
                var end = rs.getObject("end_date", LocalDateTime.class);
                return new View(
                    rs.getLong("no_smoking_record_id"),
                    epoch(rs.getObject("start_date", LocalDateTime.class)),
                    end == null ? null : epoch(end),
                    rs.getInt("daily_cigarettes"),
                    rs.getInt("cigarette_price"),
                    rs.getInt("cigarettes_per_pack")
                );
            },
            userId
        );
    }

    private void validate(Input input) {
        long now = System.currentTimeMillis();
        if (
            input.start() < 0 ||
            input.start() > now ||
            (input.end() != null && (input.end() < input.start() || input.end() > now))
        ) throw new ResponseStatusException(BAD_REQUEST, "시작일과 종료일을 확인해주세요.");
    }

    private void lock(int userId) {
        jdbc.queryForObject(
            "SELECT user_id FROM `user` WHERE user_id=? FOR UPDATE",
            Integer.class,
            userId
        );
    }

    private void activeCheck(int userId, Input input, long except) {
        if (
            input.end() == null &&
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM no_smoking_record WHERE user_id=? AND end_date IS NULL AND no_smoking_record_id<>?",
                Long.class,
                userId,
                except
            ) > 0
        ) throw new ResponseStatusException(
            CONFLICT,
            "진행 중인 기록이 있어요. 기존 기록 또는 이전할 비회원 기록을 먼저 종료해주세요."
        );
    }

    @Transactional
    public long create(int userId, Input input) {
        validate(input);
        lock(userId);
        var imported = jdbc.queryForList(
            "SELECT record_id FROM quit_record_import WHERE user_id=? AND client_id=?",
            Long.class,
            userId,
            input.clientId().toString()
        );
        if (!imported.isEmpty()) {
            String previous = jdbc.queryForObject(
                "SELECT payload_hash FROM quit_record_import WHERE user_id=? AND client_id=?",
                String.class,
                userId,
                input.clientId().toString()
            );
            if (!fingerprint(input).equals(previous)) throw new ResponseStatusException(
                CONFLICT,
                "이미 이전한 기록과 입력 내용이 달라요. 계정의 기록을 확인하고 수정해주세요. 비회원 기록은 유지됩니다."
            );
            return imported.get(0);
        }
        activeCheck(userId, input, -1);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                "INSERT INTO no_smoking_record (user_id,start_date,end_date,status,daily_cigarettes,cigarette_price,cigarettes_per_pack,created_at,updated_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setInt(1, userId);
            statement.setObject(2, local(input.start()));
            statement.setObject(3, input.end() == null ? null : local(input.end()));
            statement.setString(4, input.end() == null ? "IN_PROGRESS" : "COMPLETED");
            statement.setInt(5, input.daily());
            statement.setInt(6, input.price());
            statement.setInt(7, input.packSize());
            return statement;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        jdbc.update(
            "INSERT INTO quit_record_import (user_id,client_id,record_id,payload_hash) VALUES (?,?,?,?)",
            userId,
            input.clientId().toString(),
            id,
            fingerprint(input)
        );
        return id;
    }

    @Transactional
    public void update(int userId, long id, Input input) {
        validate(input);
        lock(userId);
        if (
            list(userId)
                .stream()
                .noneMatch(r -> r.id() == id)
        ) throw new ResponseStatusException(NOT_FOUND, "기록을 찾을 수 없어요.");
        activeCheck(userId, input, id);
        jdbc.update(
            "UPDATE no_smoking_record SET start_date=?,end_date=?,status=?,daily_cigarettes=?,cigarette_price=?,cigarettes_per_pack=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND no_smoking_record_id=?",
            local(input.start()),
            input.end() == null ? null : local(input.end()),
            input.end() == null ? "IN_PROGRESS" : "COMPLETED",
            input.daily(),
            input.price(),
            input.packSize(),
            userId,
            id
        );
    }

    @Transactional
    public void delete(int userId, long id) {
        lock(userId);
        if (
            list(userId)
                .stream()
                .noneMatch(record -> record.id() == id)
        ) throw new ResponseStatusException(NOT_FOUND, "기록을 찾을 수 없어요.");
        for (String table : List.of(
            "wishlist",
            "no_smoking_tree",
            "no_smoking_attendance",
            "no_smoking_travel_progress"
        )) {
            jdbc.update("DELETE FROM " + table + " WHERE no_smoking_record_id=?", id);
        }
        if (
            jdbc.update(
                "DELETE FROM no_smoking_record WHERE user_id=? AND no_smoking_record_id=?",
                userId,
                id
            ) == 0
        ) throw new ResponseStatusException(NOT_FOUND, "기록을 찾을 수 없어요.");
        // 재요청으로 삭제된 기록이 복구되지 않도록 이전 이력은 유지한다.
    }
}
