package com.example.demo.controller;

import com.example.demo.dto.request.SendVerificationEmailRequest;
import com.example.demo.dto.request.VerifyEmailRequest;
import com.example.demo.dto.response.EmailSendResponse;
import com.example.demo.dto.response.EmailVerifyResponse;
import com.example.demo.service.EmailVerificationService;
import com.example.demo.service.SignupService;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/email")
public class EmailVerificationController {

    private final EmailVerificationService verification;
    private final SignupService signup;

    public EmailVerificationController(
        EmailVerificationService verification,
        SignupService signup
    ) {
        this.verification = verification;
        this.signup = signup;
    }

    @PostMapping("/send")
    public EmailSendResponse send(@Valid @RequestBody SendVerificationEmailRequest request) {
        if (signup.emailExists(request.email())) throw new DuplicateKeyException("Email exists");
        verification.send(request.email());
        return new EmailSendResponse("인증번호를 보냈습니다. 메일함을 확인해주세요.", 300, 60);
    }

    @PostMapping("/verify")
    public EmailVerifyResponse verify(@Valid @RequestBody VerifyEmailRequest request) {
        return new EmailVerifyResponse(verification.verify(request.email(), request.code()), 600);
    }
}
