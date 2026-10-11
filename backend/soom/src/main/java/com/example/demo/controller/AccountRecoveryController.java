package com.example.demo.controller;

import com.example.demo.dto.request.*;
import com.example.demo.dto.response.MessageResponse;
import com.example.demo.service.AccountRecoveryService;
import com.example.demo.service.EmailRecoveryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AccountRecoveryController {

    private final AccountRecoveryService recovery;
    private final EmailRecoveryService emailRecovery;

    public AccountRecoveryController(
        AccountRecoveryService recovery,
        EmailRecoveryService emailRecovery
    ) {
        this.recovery = recovery;
        this.emailRecovery = emailRecovery;
    }

    public record FoundEmailResponse(String maskedEmail) {}

    public record HintQuestionsRequest(
        @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(min = 2, max = 20)
        String nickname
    ) {}

    public record HintQuestionsResponse(java.util.List<Integer> questionNumbers) {}

    @PostMapping("/find-id/questions")
    public HintQuestionsResponse questions(@Valid @RequestBody HintQuestionsRequest request) {
        return new HintQuestionsResponse(emailRecovery.questions(request.nickname().strip()));
    }

    @PostMapping("/find-id")
    public FoundEmailResponse findEmail(@Valid @RequestBody FindEmailRequest request) {
        return new FoundEmailResponse(emailRecovery.findEmail(request));
    }

    @PostMapping("/password/request")
    public MessageResponse send(@Valid @RequestBody RecoveryRequest request) {
        recovery.sendResetLink(request);
        return new MessageResponse(
            "이메일과 닉네임이 확인되어 재설정 링크를 보냈습니다. 메일함을 확인해주세요. 링크는 1시간 동안 유효합니다."
        );
    }

    @PostMapping("/password/validate")
    public MessageResponse validate(@Valid @RequestBody ResetTokenRequest request) {
        recovery.validate(request.token());
        return new MessageResponse("새 비밀번호를 입력해주세요.");
    }

    @PostMapping("/password/reset")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest request) {
        recovery.reset(request.token(), request.password());
        return new MessageResponse("비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요.");
    }
}
