package com.example.demo.dto.request;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record LoginRequest(
    @NotBlank @Email @Size(max = 100) String email,
    @NotBlank @Size(max = 72) String password,
    boolean rememberMe
) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
