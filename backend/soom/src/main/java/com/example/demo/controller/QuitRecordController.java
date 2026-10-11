package com.example.demo.controller;

import com.example.demo.dto.response.CurrentUserResponse;
import com.example.demo.service.QuitRecordService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/quit-records")
public class QuitRecordController {

    private final QuitRecordService records;

    public QuitRecordController(QuitRecordService records) {
        this.records = records;
    }

    @GetMapping
    public List<QuitRecordService.View> list(@AuthenticationPrincipal CurrentUserResponse user) {
        return records.list(user.userId());
    }

    @PostMapping
    public ResponseEntity<?> create(
        @AuthenticationPrincipal CurrentUserResponse user,
        @Valid @RequestBody QuitRecordService.Input input
    ) {
        return ResponseEntity.status(201).body(Map.of("id", records.create(user.userId(), input)));
    }

    @PostMapping("/import")
    public Map<String, Long> migrate(
        @AuthenticationPrincipal CurrentUserResponse user,
        @Valid @RequestBody QuitRecordService.Input input
    ) {
        return Map.of("id", records.create(user.userId(), input));
    }

    @PostMapping("/{id}/update")
    public Map<String, Boolean> update(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id,
        @Valid @RequestBody QuitRecordService.Input input
    ) {
        records.update(user.userId(), id, input);
        return Map.of("saved", true);
    }

    @PostMapping("/{id}/delete")
    public Map<String, Boolean> delete(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id
    ) {
        records.delete(user.userId(), id);
        return Map.of("deleted", true);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> invalid(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(
            Map.of("message", Objects.requireNonNullElse(ex.getReason(), "요청을 확인해주세요."))
        );
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
    })
    ResponseEntity<?> validation() {
        return ResponseEntity.badRequest().body(
            Map.of("message", "날짜, 흡연량, 한 갑 가격과 개비 수를 확인해주세요.")
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() {
        return ResponseEntity.status(409).body(
            Map.of("message", "연결된 데이터 또는 중복 기록이 있어 요청을 처리하지 못했어요.")
        );
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).body(
            Map.of("message", "기록 저장소에 연결하지 못했어요. 잠시 후 다시 시도해주세요.")
        );
    }
}
