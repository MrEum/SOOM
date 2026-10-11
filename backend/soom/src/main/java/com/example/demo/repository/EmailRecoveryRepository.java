package com.example.demo.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EmailRecoveryRepository {

    private final JdbcTemplate jdbc;

    public EmailRecoveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAnswers(int userId, List<String> hashes) {
        for (int index = 0; index < hashes.size(); index++) {
            if (hashes.get(index).isEmpty()) continue;
            jdbc.update(
                "INSERT INTO email_recovery_hint(user_id, question_number, answer_hash) VALUES (?, ?, ?)",
                userId,
                index + 1,
                hashes.get(index)
            );
        }
    }

    public List<String> findAnswers(int userId) {
        var answers = new java.util.ArrayList<String>(java.util.Collections.nCopies(5, ""));
        jdbc.query(
            "SELECT question_number, answer_hash FROM email_recovery_hint WHERE user_id = ?",
            (org.springframework.jdbc.core.RowCallbackHandler) row ->
                answers.set(row.getInt("question_number") - 1, row.getString("answer_hash")),
            userId
        );
        return answers;
    }

    public boolean recordAttempt(String nicknameHash, Instant now) {
        jdbc.update(
            """
            INSERT INTO email_recovery_attempt(nickname_hash, attempts, window_started_at) VALUES (?, 0, ?)
            ON DUPLICATE KEY UPDATE nickname_hash = nickname_hash
            """,
            nicknameHash,
            Timestamp.from(now)
        );
        jdbc.queryForObject(
            "SELECT attempts FROM email_recovery_attempt WHERE nickname_hash = ? FOR UPDATE",
            Integer.class,
            nicknameHash
        );
        jdbc.update(
            "UPDATE email_recovery_attempt SET attempts = 0, window_started_at = ? WHERE nickname_hash = ? AND window_started_at <= ?",
            Timestamp.from(now),
            nicknameHash,
            Timestamp.from(now.minusSeconds(900))
        );
        return (
            jdbc.update(
                "UPDATE email_recovery_attempt SET attempts = attempts + 1 WHERE nickname_hash = ? AND attempts < 5",
                nicknameHash
            ) == 1
        );
    }
}
