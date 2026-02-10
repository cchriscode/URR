package com.tiketi.authservice.service;

import com.tiketi.authservice.domain.UserEntity;
import com.tiketi.authservice.dto.AuthResponse;
import com.tiketi.authservice.dto.LoginRequest;
import com.tiketi.authservice.dto.MeResponse;
import com.tiketi.authservice.dto.RegisterRequest;
import com.tiketi.authservice.dto.UserPayload;
import com.tiketi.authservice.dto.VerifyTokenResponse;
import com.tiketi.authservice.exception.ApiException;
import com.tiketi.authservice.repository.UserRepository;
import com.tiketi.authservice.security.JwtService;
import io.jsonwebtoken.JwtException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RestClient googleRestClient;
    private final String googleClientId;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        @Value("${GOOGLE_CLIENT_ID:}") String googleClientId
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleClientId = googleClientId;
        this.googleRestClient = RestClient.builder().baseUrl("https://oauth2.googleapis.com").build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            throw new ApiException("Email already exists");
        });

        UserEntity user = new UserEntity();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setPhone(request.phone());

        UserEntity saved = userRepository.save(user);
        String token = jwtService.generateToken(saved);

        return new AuthResponse("Registration completed", token, UserPayload.from(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ApiException("Invalid email or password"));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ApiException("Invalid email or password");
        }

        boolean passwordMatched;
        try {
            passwordMatched = passwordEncoder.matches(request.password(), user.getPasswordHash());
        } catch (IllegalArgumentException ex) {
            passwordMatched = false;
        }

        if (!passwordMatched) {
            throw new ApiException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse("Login successful", token, UserPayload.from(user));
    }

    @Transactional(readOnly = true)
    public MeResponse me(String bearerToken) {
        String token = extractToken(bearerToken);
        String userId;
        try {
            userId = jwtService.extractUserId(token);
        } catch (JwtException ex) {
            throw new ApiException("Invalid or expired token");
        }

        UserEntity user = userRepository.findById(java.util.UUID.fromString(userId))
            .orElseThrow(() -> new ApiException("User not found"));

        return new MeResponse(new MeResponse.MeUser(
            user.getId().toString(),
            user.getEmail(),
            user.getName(),
            user.getRole().name(),
            user.getCreatedAt() != null ? user.getCreatedAt().toString() : null));
    }

    @Transactional(readOnly = true)
    public VerifyTokenResponse verifyToken(String token) {
        String userId;
        try {
            userId = jwtService.extractUserId(token);
        } catch (JwtException ex) {
            throw new ApiException("Invalid or expired token");
        }

        UserEntity user = userRepository.findById(java.util.UUID.fromString(userId))
            .orElseThrow(() -> new ApiException("User not found"));

        return new VerifyTokenResponse(true,
            new VerifyTokenResponse.VerifiedUser(
                user.getId().toString(),
                user.getEmail(),
                user.getName(),
                user.getRole().name()));
    }

    @Transactional
    public Map<String, Object> googleLogin(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new ApiException("Google credential is required");
        }
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ApiException("GOOGLE_CLIENT_ID is not configured");
        }

        Map<String, Object> payload;
        try {
            payload = googleRestClient.get()
                .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", credential).build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ApiException("Invalid Google token");
                })
                .body(new ParameterizedTypeReference<>() {
                });
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("Invalid Google token");
        }

        if (payload == null) {
            throw new ApiException("Invalid Google token");
        }

        String audience = toStringValue(payload.get("aud"));
        if (!googleClientId.equals(audience)) {
            throw new ApiException("Invalid Google audience");
        }

        String googleId = toStringValue(payload.get("sub"));
        String email = toStringValue(payload.get("email"));
        String name = toStringValue(payload.get("name"));
        String picture = toStringValue(payload.get("picture"));

        if (googleId == null || googleId.isBlank() || email == null || email.isBlank()) {
            throw new ApiException("Invalid Google token payload");
        }

        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = new UserEntity();
            user.setEmail(email);
            user.setName((name == null || name.isBlank()) ? email : name);
            user.setGoogleId(googleId);
            user.setPasswordHash("");
            user = userRepository.save(user);
        } else if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {
            user.setGoogleId(googleId);
            user = userRepository.save(user);
        }

        String token = jwtService.generateToken(user);

        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("id", user.getId().toString());
        userPayload.put("userId", user.getId().toString());
        userPayload.put("email", user.getEmail());
        userPayload.put("name", user.getName());
        userPayload.put("role", user.getRole().name());
        userPayload.put("picture", picture);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Google login successful");
        response.put("token", token);
        response.put("user", userPayload);
        return response;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findUserById(UUID id) {
        UserEntity user = userRepository.findById(id)
            .orElseThrow(() -> new ApiException("User not found"));
        return toUserMap(user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findUsersByIds(List<UUID> ids) {
        return userRepository.findAllById(ids).stream().map(this::toUserMap).toList();
    }

    private Map<String, Object> toUserMap(UserEntity user) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", user.getId().toString());
        out.put("email", user.getEmail());
        out.put("name", user.getName());
        out.put("role", user.getRole().name());
        return out;
    }

    private String extractToken(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new ApiException("No token provided");
        }
        return bearerToken.substring(7);
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
