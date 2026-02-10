package com.tiketi.paymentservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JwtTokenParser {

    private final SecretKey key;

    public JwtTokenParser(@Value("${JWT_SECRET:dev-only-secret-change-in-production-f8a7b6c5d4e3f2a1}") String secret) {
        this.key = buildKey(secret);
    }

    public AuthUser requireUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String token = authorization.substring(7);
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String userId = claims.get("userId", String.class);
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            if (userId == null || userId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
            }
            return new AuthUser(userId, email, role != null ? role : "user");
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private SecretKey buildKey(String secret) {
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        } catch (Exception ignored) {
            byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
            if (raw.length < 32) {
                byte[] padded = new byte[32];
                System.arraycopy(raw, 0, padded, 0, raw.length);
                return Keys.hmacShaKeyFor(padded);
            }
            return Keys.hmacShaKeyFor(raw);
        }
    }
}
