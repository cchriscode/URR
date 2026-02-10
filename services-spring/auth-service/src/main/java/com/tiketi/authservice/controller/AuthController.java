package com.tiketi.authservice.controller;

import com.tiketi.authservice.dto.AuthResponse;
import com.tiketi.authservice.dto.GoogleLoginRequest;
import com.tiketi.authservice.dto.LoginRequest;
import com.tiketi.authservice.dto.MeResponse;
import com.tiketi.authservice.dto.RegisterRequest;
import com.tiketi.authservice.dto.VerifyTokenRequest;
import com.tiketi.authservice.dto.VerifyTokenResponse;
import com.tiketi.authservice.exception.ApiException;
import com.tiketi.authservice.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(authorization);
    }

    @PostMapping("/verify-token")
    public VerifyTokenResponse verifyToken(@Valid @RequestBody VerifyTokenRequest request) {
        return authService.verifyToken(request.token());
    }

    @PostMapping("/google")
    public Map<String, Object> google(@RequestBody(required = false) GoogleLoginRequest request) {
        if (request == null || request.credential() == null || request.credential().isBlank()) {
            throw new ApiException("Google credential is required");
        }
        return authService.googleLogin(request.credential());
    }
}
