package com.tiketi.authservice.controller;

import com.tiketi.authservice.dto.AuthResponse;
import com.tiketi.authservice.dto.GoogleLoginRequest;
import com.tiketi.authservice.dto.LoginRequest;
import com.tiketi.authservice.dto.MeResponse;
import com.tiketi.authservice.dto.RefreshTokenRequest;
import com.tiketi.authservice.dto.RegisterRequest;
import com.tiketi.authservice.dto.VerifyTokenRequest;
import com.tiketi.authservice.dto.VerifyTokenResponse;
import com.tiketi.authservice.exception.ApiException;
import com.tiketi.authservice.service.AuthService;
import com.tiketi.authservice.util.CookieHelper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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
    private final CookieHelper cookieHelper;

    public AuthController(AuthService authService, CookieHelper cookieHelper) {
        this.authService = authService;
        this.cookieHelper = cookieHelper;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                  HttpServletResponse response) {
        AuthResponse authResponse = authService.register(request);
        cookieHelper.addAccessTokenCookie(response, authResponse.token());
        cookieHelper.addRefreshTokenCookie(response, authResponse.refreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request);
        cookieHelper.addAccessTokenCookie(response, authResponse.token());
        cookieHelper.addRefreshTokenCookie(response, authResponse.refreshToken());
        return authResponse;
    }

    @GetMapping("/me")
    public MeResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(authorization);
    }

    @PostMapping("/verify-token")
    public VerifyTokenResponse verifyToken(@Valid @RequestBody VerifyTokenRequest request) {
        return authService.verifyToken(request.token());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshTokenCookie,
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletResponse response) {
        String refreshToken = refreshTokenCookie;
        if ((refreshToken == null || refreshToken.isBlank()) && request != null) {
            refreshToken = request.refreshToken();
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException("Refresh token is required");
        }
        AuthResponse authResponse = authService.refreshToken(refreshToken);
        cookieHelper.addAccessTokenCookie(response, authResponse.token());
        cookieHelper.addRefreshTokenCookie(response, authResponse.refreshToken());
        return authResponse;
    }

    @PostMapping("/google")
    public Map<String, Object> google(@RequestBody(required = false) GoogleLoginRequest request,
                                       HttpServletResponse response) {
        if (request == null || request.credential() == null || request.credential().isBlank()) {
            throw new ApiException("Google credential is required");
        }
        Map<String, Object> result = authService.googleLogin(request.credential());
        Object token = result.get("token");
        Object refreshToken = result.get("refreshToken");
        if (token instanceof String t) {
            cookieHelper.addAccessTokenCookie(response, t);
        }
        if (refreshToken instanceof String rt) {
            cookieHelper.addRefreshTokenCookie(response, rt);
        }
        return result;
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpServletResponse response) {
        cookieHelper.clearAuthCookies(response);
        return Map.of("message", "Logged out successfully");
    }
}
