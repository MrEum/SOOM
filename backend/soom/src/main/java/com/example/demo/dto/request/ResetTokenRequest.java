package com.example.demo.dto.request;

import jakarta.validation.constraints.*;

public record ResetTokenRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
    @Override
    public String toString() {
        return "ResetTokenRequest[REDACTED]";
    }
}
