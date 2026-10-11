package com.example.demo.controller;

import com.example.demo.dto.response.MessageResponse;
import com.example.demo.exception.InvalidSignupInputException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(
    assignableTypes = {
        SignupController.class,
        EmailVerificationController.class,
        AccountRecoveryController.class,
    }
)
public class SignupExceptionHandler {

    @ExceptionHandler(com.example.demo.service.EmailVerificationService.VerificationException.class)
    ResponseEntity<MessageResponse> verification(
        com.example.demo.service.EmailVerificationService.VerificationException e
    ) {
        return ResponseEntity.status(e.status).body(new MessageResponse(e.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<MessageResponse> duplicate() {
        return ResponseEntity.status(409).body(
            new MessageResponse("이미 사용 중인 이메일 또는 닉네임입니다.")
        );
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
    })
    ResponseEntity<MessageResponse> invalid() {
        return ResponseEntity.badRequest().body(
            new MessageResponse(
                "입력 항목을 확인해주세요. 비밀번호는 영문·숫자·특수문자를 포함한 8~20자입니다."
            )
        );
    }

    @ExceptionHandler({
        HandlerMethodValidationException.class,
        MissingServletRequestParameterException.class,
    })
    ResponseEntity<MessageResponse> invalidAvailability() {
        return ResponseEntity.badRequest().body(
            new MessageResponse("올바른 이메일(최대 100자) 또는 닉네임(2~20자)을 입력해주세요.")
        );
    }

    @ExceptionHandler(InvalidSignupInputException.class)
    ResponseEntity<MessageResponse> invalidNickname() {
        return ResponseEntity.badRequest().body(
            new MessageResponse(
                "입력값을 확인해주세요. 닉네임은 공백을 제외하고 2~20자여야 합니다."
            )
        );
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<MessageResponse> database() {
        return ResponseEntity.status(503).body(
            new MessageResponse("계정 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.")
        );
    }
}
