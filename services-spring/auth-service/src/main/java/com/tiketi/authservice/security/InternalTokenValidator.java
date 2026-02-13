package com.tiketi.authservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalTokenValidator {

    private final String internalToken;

    public InternalTokenValidator(@Value("${INTERNAL_API_TOKEN}") String internalToken) {
        this.internalToken = internalToken;
    }

    public boolean isValid(String authorization, String xInternalToken) {
        if (xInternalToken != null && !xInternalToken.isBlank()) {
            return timingSafeEquals(internalToken, xInternalToken);
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        String token = authorization.substring(7);
        return timingSafeEquals(internalToken, token);
    }

    private static boolean timingSafeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
