package com.tiketi.authservice.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.tiketi.authservice.domain.UserEntity;
import com.tiketi.authservice.dto.AuthResponse;
import com.tiketi.authservice.dto.LoginRequest;
import com.tiketi.authservice.dto.MeResponse;
import com.tiketi.authservice.dto.RegisterRequest;
import com.tiketi.authservice.dto.UserPayload;
import com.tiketi.authservice.dto.VerifyTokenResponse;
import com.tiketi.authservice.domain.RefreshTokenEntity;
import com.tiketi.authservice.exception.ApiException;
import com.tiketi.authservice.repository.RefreshTokenRepository;
import com.tiketi.authservice.repository.UserRepository;
import com.tiketi.authservice.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        @Value("${GOOGLE_CLIENT_ID:}") String googleClientId
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        if (googleClientId != null && !googleClientId.isBlank()) {
            this.googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();
        } else {
            this.googleIdTokenVerifier = null;
        }
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
        UUID familyId = UUID.randomUUID();
        String refreshToken = jwtService.generateRefreshToken(saved, familyId);
        storeRefreshToken(saved.getId(), refreshToken, familyId);

        return new AuthResponse("Registration completed", token, refreshToken, UserPayload.from(saved));
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
        UUID familyId = UUID.randomUUID();
        String refreshToken = jwtService.generateRefreshToken(user, familyId);
        storeRefreshToken(user.getId(), refreshToken, familyId);
        return new AuthResponse("Login successful", token, refreshToken, UserPayload.from(user));
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        Claims claims;
        try {
            claims = jwtService.validateRefreshToken(refreshTokenValue);
        } catch (JwtException ex) {
            throw new ApiException("Invalid or expired refresh token");
        }

        String userId = claims.get("userId", String.class);
        if (userId == null) {
            throw new ApiException("Invalid refresh token");
        }

        // Token rotation: check DB for reuse detection
        String tokenHash = JwtService.hashToken(refreshTokenValue);
        var storedToken = refreshTokenRepository.findByTokenHash(tokenHash).orElse(null);

        if (storedToken != null) {
            if (storedToken.isRevoked()) {
                // Token reuse detected — revoke entire family (potential theft)
                log.warn("Refresh token reuse detected for user={}, family={}", userId, storedToken.getFamilyId());
                refreshTokenRepository.revokeAllByFamilyId(storedToken.getFamilyId());
                throw new ApiException("Token reuse detected. All sessions revoked for security.");
            }
            // Revoke this token (single use)
            storedToken.setRevokedAt(java.time.Instant.now());
            refreshTokenRepository.save(storedToken);
        }
        // If token not in DB (pre-migration token), allow refresh but store the new one

        UserEntity user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ApiException("User not found"));

        UUID familyId = storedToken != null ? storedToken.getFamilyId() : UUID.randomUUID();
        String newAccessToken = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user, familyId);
        storeRefreshToken(user.getId(), newRefreshToken, familyId);

        return new AuthResponse("Token refreshed", newAccessToken, newRefreshToken, UserPayload.from(user));
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
        if (googleIdTokenVerifier == null) {
            throw new ApiException("GOOGLE_CLIENT_ID is not configured");
        }

        GoogleIdToken idToken;
        try {
            idToken = googleIdTokenVerifier.verify(credential);
        } catch (Exception ex) {
            log.warn("Google token verification failed: {}", ex.getMessage());
            throw new ApiException("Invalid Google token");
        }

        if (idToken == null) {
            throw new ApiException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String googleId = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String picture = (String) payload.get("picture");

        if (googleId == null || googleId.isBlank() || email == null || email.isBlank()) {
            throw new ApiException("Invalid Google token payload");
        }

        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = new UserEntity();
            user.setEmail(email);
            user.setName((name == null || name.isBlank()) ? email : name);
            user.setGoogleId(googleId);
            user.setPasswordHash("OAUTH_USER_NO_PASSWORD");
            user = userRepository.save(user);
        } else if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {
            user.setGoogleId(googleId);
            user = userRepository.save(user);
        }

        String token = jwtService.generateToken(user);
        UUID familyId = UUID.randomUUID();
        String refreshToken = jwtService.generateRefreshToken(user, familyId);
        storeRefreshToken(user.getId(), refreshToken, familyId);

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
        response.put("refreshToken", refreshToken);
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

    @Transactional
    public void revokeAllTokens(String bearerToken) {
        String token = extractToken(bearerToken);
        String userId = jwtService.extractUserId(token);
        refreshTokenRepository.revokeAllByUserId(UUID.fromString(userId));
    }

    private void storeRefreshToken(UUID userId, String rawToken, UUID familyId) {
        RefreshTokenEntity entity = new RefreshTokenEntity();
        entity.setUserId(userId);
        entity.setTokenHash(JwtService.hashToken(rawToken));
        entity.setFamilyId(familyId);
        entity.setExpiresAt(java.time.Instant.now().plusSeconds(jwtService.getRefreshTokenExpirationSeconds()));
        entity.setCreatedAt(java.time.Instant.now());
        refreshTokenRepository.save(entity);
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
