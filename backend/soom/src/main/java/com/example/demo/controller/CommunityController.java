package com.example.demo.controller;

import com.example.demo.dto.response.CurrentUserResponse;
import com.example.demo.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService service;
    private final DashboardService dashboard;

    public CommunityController(CommunityService service, DashboardService dashboard) {
        this.service = service;
        this.dashboard = dashboard;
    }

    public record Post(
        Long id,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String content,
        @NotBlank String category,
        @NotNull @Size(max = 4) List<@NotNull @Size(max = 700000) String> images,
        @NotNull UUID requestId
    ) {}

    public record Like(@NotNull Boolean liked) {}

    public record Comment(
        Long parent,
        @NotBlank @Size(max = 1000) String content,
        @NotNull UUID requestId
    ) {}

    public record EditComment(@NotBlank @Size(max = 1000) String content) {}

    public record Report(
        @NotBlank String type,
        @Positive long id,
        @NotBlank @Size(max = 255) String reason
    ) {}

    @GetMapping
    public CommunityService.Feed list(@AuthenticationPrincipal CurrentUserResponse user) {
        return service.feed(user == null ? null : user.userId());
    }

    @GetMapping("/catalog")
    public DashboardService.Catalog catalog() {
        return dashboard.catalog();
    }

    @PostMapping("/{id}/view")
    public CommunityService.Feed view(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id
    ) {
        return service.view(user == null ? null : user.userId(), id);
    }

    @GetMapping("/images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        var image = service.image(id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.mime()))
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.noCache())
            .body(image.bytes());
    }

    @PostMapping("/posts")
    public CommunityService.Feed save(
        @AuthenticationPrincipal CurrentUserResponse user,
        @Valid @RequestBody Post input
    ) {
        return service.save(
            user.userId(),
            input.id(),
            input.title(),
            input.content(),
            input.category(),
            input.images(),
            input.requestId()
        );
    }

    @PostMapping("/{id}/delete")
    public CommunityService.Feed delete(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id
    ) {
        return service.delete(user.userId(), id);
    }

    @PostMapping("/{id}/like")
    public CommunityService.Feed like(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id,
        @Valid @RequestBody Like input
    ) {
        return service.like(user.userId(), id, input.liked());
    }

    @PostMapping("/{id}/comments")
    public CommunityService.Feed comment(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id,
        @Valid @RequestBody Comment input
    ) {
        return service.comment(
            user.userId(),
            id,
            input.parent(),
            input.content(),
            input.requestId()
        );
    }

    @PostMapping("/comments/{id}/update")
    public CommunityService.Feed edit(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id,
        @Valid @RequestBody EditComment input
    ) {
        return service.editComment(user.userId(), id, input.content(), false);
    }

    @PostMapping("/comments/{id}/delete")
    public CommunityService.Feed removeComment(
        @AuthenticationPrincipal CurrentUserResponse user,
        @PathVariable long id
    ) {
        return service.editComment(user.userId(), id, null, true);
    }

    @PostMapping("/reports")
    public CommunityService.Feed report(
        @AuthenticationPrincipal CurrentUserResponse user,
        @Valid @RequestBody Report input
    ) {
        return service.report(user.userId(), input.type(), input.id(), input.reason());
    }
}
