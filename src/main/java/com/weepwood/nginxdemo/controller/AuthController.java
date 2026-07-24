package com.weepwood.nginxdemo.controller;

import com.weepwood.nginxdemo.config.DemoAuthProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DemoAuthProperties properties;

    public AuthController(DemoAuthProperties properties) {
        this.properties = properties;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        boolean usernameMatches = constantTimeEquals(request.username(), properties.getUsername());
        boolean passwordMatches = constantTimeEquals(request.password(), properties.getPassword());
        if (!usernameMatches || !passwordMatches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return new LoginResponse(properties.getToken(), "Bearer", true);
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record LoginResponse(String token, String tokenType, boolean demoOnly) {}
}
