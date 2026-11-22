package com.example.library.controller;

import com.example.library.annotation.CurrentUsername;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me")
public class ProfileController {

    /** Ім'я користувача підставляє CurrentUsernameArgumentResolver (@CurrentUsername). */
    @GetMapping("/greeting")
    public Map<String, String> greeting(@CurrentUsername String username) {
        return Map.of("username", username, "greeting", "Вітаємо, " + username + "!");
    }
}
