package com.example.demo.controller;

import com.example.demo.dto.response.CurrentUserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminAccessController {

    @GetMapping("/access")
    public CurrentUserResponse access(@AuthenticationPrincipal CurrentUserResponse user) {
        return user;
    }
}
