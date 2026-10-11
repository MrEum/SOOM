package com.example.demo.dto.request;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record RecoveryRequest(
    @NotBlank @Size(min = 2, max = 20) String nickname,
    @NotBlank @Email @Size(max = 100) String email
) {
    public RecoveryRequest {
        nickname = nickname == null ? null : nickname.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
