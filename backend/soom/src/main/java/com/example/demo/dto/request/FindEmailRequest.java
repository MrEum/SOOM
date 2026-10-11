package com.example.demo.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record FindEmailRequest(
    @NotBlank @Size(min = 2, max = 20) String nickname,
    @NotNull @Size(min = 5, max = 5) List<@NotNull @Size(max = 40) String> recoveryAnswers
) {
    public FindEmailRequest {
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
        return "FindEmailRequest[REDACTED]";
    }
}
