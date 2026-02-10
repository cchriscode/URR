package com.tiketi.authservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalTokenValidator {

    private final String internalToken;

    public InternalTokenValidator(@Value("${INTERNAL_API_TOKEN:dev-internal-token-change-me}") String internalToken) {
        this.internalToken = internalToken;
    }

    public boolean isValid(String authorization, String xInternalToken) {
        if (xInternalToken != null && !xInternalToken.isBlank()) {
            return internalToken.equals(xInternalToken);
        }
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        String token = authorization.substring(7);
        return internalToken.equals(token);
    }
}
