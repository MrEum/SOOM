package com.example.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record SendVerificationEmailRequest(@NotBlank @Email @Size(max = 100) String email) {
    public SendVerificationEmailRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
