package com.example.demo.controller;

import com.example.demo.dto.request.SignupRequest;
import com.example.demo.dto.response.AvailabilityResponse;
import com.example.demo.dto.response.MessageResponse;
import com.example.demo.exception.InvalidSignupInputException;
import com.example.demo.service.SignupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class SignupController {

    private final SignupService service;

    public SignupController(SignupService service) {
        this.service = service;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse signup(@Valid @RequestBody SignupRequest request) {
        service.signup(request);
        return new MessageResponse("회원가입이 완료되었습니다.");
    }

    @GetMapping("/check-email")
    public AvailabilityResponse email(
        @RequestParam @NotBlank @Email @Size(max = 100) String email
    ) {
        return new AvailabilityResponse(
            !service.emailExists(email.strip().toLowerCase(Locale.ROOT))
        );
    }

    @GetMapping("/check-nickname")
    public AvailabilityResponse nickname(
        @RequestParam @NotBlank @Size(min = 2, max = 20) String nickname
    ) {
        if (nickname.strip().length() < 2) throw new InvalidSignupInputException(
            "닉네임은 2글자 이상 입력해주세요."
        );
        return new AvailabilityResponse(!service.nicknameExists(nickname.strip()));
    }
}
