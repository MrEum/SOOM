package com.example.demo.controller;

import com.example.demo.dto.response.MessageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = LoginController.class)
public class LoginExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<MessageResponse> auth(AuthenticationException e) {
        return ResponseEntity.status(e instanceof DisabledException ? 403 : 401).body(
            new MessageResponse(e.getMessage())
        );
    }

    @ExceptionHandler({
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
    })
    ResponseEntity<MessageResponse> input() {
        return ResponseEntity.badRequest().body(
            new MessageResponse("이메일과 비밀번호를 확인해주세요.")
        );
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    ResponseEntity<MessageResponse> database() {
        return ResponseEntity.status(503).body(
            new MessageResponse("로그인 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요.")
        );
    }
}
