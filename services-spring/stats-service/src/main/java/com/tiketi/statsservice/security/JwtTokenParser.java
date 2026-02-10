package com.tiketi.statsservice.security;

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

    public AuthUser requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String token = authorization.substring(7);
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            AuthUser user = new AuthUser(
                claims.get("userId", String.class),
                claims.get("email", String.class),
                claims.get("role", String.class)
            );
            if (!user.isAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
            }
            return user;
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
