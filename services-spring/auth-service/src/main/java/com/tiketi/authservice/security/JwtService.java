package com.tiketi.authservice.security;

import com.tiketi.authservice.config.JwtProperties;
import com.tiketi.authservice.domain.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

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

    private Key signingKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        } catch (IllegalArgumentException ex) {
            keyBytes = jwtProperties.secret().getBytes();
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
