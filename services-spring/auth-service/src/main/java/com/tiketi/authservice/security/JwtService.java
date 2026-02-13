package com.tiketi.authservice.security;

import com.tiketi.authservice.config.JwtProperties;
import com.tiketi.authservice.domain.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(UserEntity user) {
        long expirationMillis = jwtProperties.expirationSeconds() * 1000;
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("userId", user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey())
            .compact();
    }

    public String generateRefreshToken(UserEntity user) {
        long expirationMillis = jwtProperties.refreshTokenExpirationSeconds() * 1000;
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("userId", user.getId().toString())
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(signingKey())
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith((javax.crypto.SecretKey) signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractUserId(String token) {
        return parse(token).get("userId", String.class);
    }

    public Claims validateRefreshToken(String token) {
        Claims claims = parse(token);
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            throw new io.jsonwebtoken.JwtException("Token is not a refresh token");
        }
        return claims;
    }

    private Key signingKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT_SECRET is not valid Base64, using raw bytes");
            keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
