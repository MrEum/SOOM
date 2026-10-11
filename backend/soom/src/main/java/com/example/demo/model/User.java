package com.example.demo.model;

import java.time.LocalDateTime;

public record User(
    int userId,
    String email,
    String password,
    String name,
    String nickname,
    String role,
    String status,
    LocalDateTime emailVerifiedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime deletedAt,
    LocalDateTime scheduledDeleteAt
) {
    @Override
    public String toString() {
        return "User[userId=" + userId + ", role=" + role + ", status=" + status + "]";
    }
}
