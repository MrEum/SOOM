package com.example.demo.dto.response;

public record EmailVerifyResponse(String verificationToken, int expiresIn) {}
