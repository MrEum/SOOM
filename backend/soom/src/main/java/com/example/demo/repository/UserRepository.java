package com.example.demo.repository;

import com.example.demo.model.User;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final String SELECT_USER = """
    SELECT user_id, email, password, name, nickname, role, status,
           email_verified_at, created_at, updated_at, deleted_at, scheduled_delete_at
    FROM `user`
    """;

    private static final RowMapper<User> USER_MAPPER = (rs, rowNum) ->
        new User(
            rs.getInt("user_id"),
            rs.getString("email"),
            rs.getString("password"),
            rs.getString("name"),
            rs.getString("nickname"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getObject("email_verified_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class),
            rs.getObject("deleted_at", LocalDateTime.class),
            rs.getObject("scheduled_delete_at", LocalDateTime.class)
        );

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<User> findAll() {
        return jdbc.query(SELECT_USER, USER_MAPPER);
    }

    public Optional<User> findByEmail(String email) {
        return jdbc
            .query(SELECT_USER + " WHERE email = ?", USER_MAPPER, email)
            .stream()
            .findFirst();
    }

    public Optional<User> findById(int userId) {
        return jdbc
            .query(SELECT_USER + " WHERE user_id = ?", USER_MAPPER, userId)
            .stream()
            .findFirst();
    }

    public Optional<User> findByNickname(String nickname) {
        return jdbc
            .query(SELECT_USER + " WHERE nickname = ?", USER_MAPPER, nickname)
            .stream()
            .findFirst();
    }

    public boolean existsByEmail(String email) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM `user` WHERE email = ?)",
                Boolean.class,
                email
            )
        );
    }

    public boolean existsByNickname(String nickname) {
        return Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM `user` WHERE nickname = ?)",
                Boolean.class,
                nickname
            )
        );
    }

    public int insert(String email, String passwordHash, String name, String nickname) {
        var generatedKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                """
                INSERT INTO `user`
                    (email, password, name, nickname, role, status,
                     created_at, updated_at, email_verified_at)
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, email);
            statement.setString(2, passwordHash);
            statement.setString(3, name);
            statement.setString(4, nickname);
            return statement;
        }, generatedKey);
        return java.util.Objects.requireNonNull(generatedKey.getKey()).intValue();
    }
}
