package com.example.demo.dto.request;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
    @NotBlank
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,20}$")
    String password
) {
    @Override
    public String toString() {
        return "ResetPasswordRequest[REDACTED]";
    }
}
