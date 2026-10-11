package com.example.demo.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Locale;

public record SignupRequest(
    @NotBlank @Email @Size(max = 100) String email,
    @NotBlank
    @Size(min = 8, max = 20)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,20}$")
    String password,
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Size(min = 2, max = 20) String nickname,
    @NotBlank @Size(max = 100) String verificationToken,
    @NotNull @Size(min = 5, max = 5) List<@NotNull @Size(max = 40) String> recoveryAnswers
) {
    public SignupRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        name = name == null ? null : name.strip();
        nickname = nickname == null ? null : nickname.strip();
    }

    @AssertTrue(message = "힌트를 하나 이상 선택하고 답변해주세요.")
    public boolean isRecoveryAnswerSelected() {
        return (
            recoveryAnswers != null &&
            recoveryAnswers.stream().anyMatch(a -> a != null && !a.isBlank())
        );
    }

    @Override
    public String toString() {
        return "SignupRequest[REDACTED]";
    }
}
