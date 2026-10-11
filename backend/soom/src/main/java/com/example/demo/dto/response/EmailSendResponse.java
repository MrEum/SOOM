package com.example.demo.dto.response;

public record EmailSendResponse(String message, int expiresIn, int retryAfter) {}
