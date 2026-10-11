package com.example.demo.dto.response;

public record CurrentUserResponse(
    int userId,
    String email,
    String name,
    String nickname,
    String role
) {}
