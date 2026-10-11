package com.example.demo.controller;

import com.example.demo.dto.response.CurrentUserResponse;
import com.example.demo.service.DashboardService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    public record RecordAction(@Positive long recordId) {}

    public record Nurture(
        @Positive long recordId,
        @NotBlank String kind,
        @NotNull UUID requestId
    ) {}

    public record Wish(
        Long id,
        @Positive long recordId,
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(1000000000) int price,
        @NotNull UUID requestId
    ) {}

    @GetMapping
    public DashboardService.Snapshot get(@AuthenticationPrincipal CurrentUserResponse user) {
        return service.snapshot(user.userId());
    }

    @PostMapping("/sync")
    public DashboardService.Snapshot sync(@AuthenticationPrincipal CurrentUserResponse user) {
        return service.sync(user.userId());
    }

    @PostMapping("/attendance")
    public DashboardService.Snapshot attend(
        @AuthenticationPrincipal CurrentUserResponse user,
        @Valid @RequestBody RecordAction input
    ) {
        return service.attend(user.userId(), input.recordId());
    }

    @PostMapping("/tree")
    public DashboardService.Snapshot tree(
        @AuthenticationPrincipal CurrentUserResponse user,
        @Valid @RequestBody Nurture input
    ) {
        return service.nurture(user.userId(), input.recordId(), input.kind(), input.requestId());
    }

    @PostMapping("/wishes")
    public DashboardService.Snapshot wish(
        @AuthenticationPrincipal CurrentUserResponse user,
        @Valid @RequestBody Wish input
    ) {
        return service.saveWish(
            user.userId(),
            input.id(),
            input.recordId(),
            input.name(),
            input.price(),
            input.requestId()
        );
    }

    @PostMapping("/wishes/{id}/delete")
    public DashboardService.Snapshot delete(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id
    ) {
        return service.deleteWish(user.userId(), id);
    }

    @PostMapping("/notifications/{id}/read")
    public DashboardService.Snapshot read(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id
    ) {
        return service.readNotice(user.userId(), id);
    }

    @PostMapping("/notifications/{id}/delete")
    public DashboardService.Snapshot deleteNotice(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id
    ) {
        return service.deleteNotice(user.userId(), id);
    }

    @PostMapping("/notifications/delete-all")
    public DashboardService.Snapshot deleteAllNotices(
        @AuthenticationPrincipal CurrentUserResponse user
    ) {
        return service.deleteAllNotices(user.userId());
    }
}
