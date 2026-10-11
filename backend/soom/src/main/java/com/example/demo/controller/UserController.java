package com.example.demo.controller;

import com.example.demo.dto.response.CurrentUserResponse;
import com.example.demo.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public List<CurrentUserResponse> getUsers() {
        return userService.findAllUsers();
    }
}
