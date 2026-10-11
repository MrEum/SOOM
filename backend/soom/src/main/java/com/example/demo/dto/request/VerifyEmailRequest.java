package com.example.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record VerifyEmailRequest(
    @NotBlank @Email @Size(max = 100) String email,
    @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
    public VerifyEmailRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
