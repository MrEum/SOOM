package com.example.demo.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PasswordResetRepository {

    private final JdbcTemplate jdbc;

    public PasswordResetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockUser(int userId) {
        jdbc.queryForObject(
            "SELECT user_id FROM `user` WHERE user_id = ? FOR UPDATE",
            Integer.class,
            userId
        );
    }

    public boolean recentlySent(int userId, Instant now) {
        return !jdbc
            .queryForList(
                "SELECT user_id FROM password_reset_token WHERE user_id = ? AND sent_at > ?",
                Integer.class,
                userId,
                Timestamp.from(now.minusSeconds(60))
            )
            .isEmpty();
    }

    public void replace(int userId, String tokenHash, Instant now) {
        jdbc.update("DELETE FROM password_reset_token WHERE user_id = ?", userId);
        jdbc.update(
            "INSERT INTO password_reset_token(user_id, token_hash, expires_at, sent_at) VALUES (?, ?, ?, ?)",
            userId,
            tokenHash,
            Timestamp.from(now.plusSeconds(3600)),
            Timestamp.from(now)
        );
    }

    public Optional<Integer> findUser(String tokenHash, Instant now) {
        return jdbc
            .queryForList(
                "SELECT user_id FROM password_reset_token WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?",
                Integer.class,
                tokenHash,
                Timestamp.from(now)
            )
            .stream()
            .findFirst();
    }

    public boolean consume(String tokenHash, Instant now) {
        return (
            jdbc.update(
                "UPDATE password_reset_token SET used_at = ? WHERE token_hash = ? AND used_at IS NULL AND expires_at > ?",
                Timestamp.from(now),
                tokenHash,
                Timestamp.from(now)
            ) == 1
        );
    }

    public void updatePassword(int userId, String passwordHash) {
        jdbc.update(
            "UPDATE `user` SET password = ?, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?",
            passwordHash,
            userId
        );
    }
}
