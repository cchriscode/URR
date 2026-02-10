package com.tiketi.ticketservice.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InternalTokenValidator {

    private final String internalToken;

    public InternalTokenValidator(@Value("${INTERNAL_API_TOKEN:dev-internal-token-change-me}") String internalToken) {
        this.internalToken = internalToken;
    }

    public void requireValidToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal token required");
        }
        String token = authorization.substring(7);
        if (!internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal token");
        }
    }
}
