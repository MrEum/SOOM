package com.example.demo;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://127.0.0.1:5173")
public class HealthCheckController {

    @GetMapping("/test/hello")
    @ResponseBody
    public String hello() {
        return "hello. 안녕하세요.";
    }
}
