package com.example.demo.service;

import com.example.demo.dto.response.CurrentUserResponse;
import com.example.demo.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<CurrentUserResponse> findAllUsers() {
        return userRepository
            .findAll()
            .stream()
            .map(user ->
                new CurrentUserResponse(
                    user.userId(),
                    user.email(),
                    user.name(),
                    user.nickname(),
                    user.role()
                )
            )
            .toList();
    }
}
