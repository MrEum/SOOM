package com.example.demo.controller;

import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = { DashboardController.class, CommunityController.class })
public class DashboardExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> invalid(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(
            Map.of("message", Objects.requireNonNullElse(e.getReason(), "요청을 확인해주세요."))
        );
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
    })
    ResponseEntity<?> input() {
        return ResponseEntity.badRequest().body(
            Map.of("message", "입력 값과 형식을 확인해주세요.")
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(
            Map.of(
                "message",
                "이미 처리되었거나 연결된 데이터가 있습니다. 새로고침 후 확인해주세요."
            )
        );
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).body(
            Map.of("message", "데이터를 불러오거나 저장하지 못했습니다. 잠시 후 다시 시도해주세요.")
        );
    }
}
