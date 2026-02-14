package com.tiketi.queueservice.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JwtTokenParser {

    public AuthUser requireUser(HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String email = request.getHeader("X-User-Email");
        String role = request.getHeader("X-User-Role");
        return new AuthUser(userId, email, role != null ? role : "user");
    }

    public AuthUser requireAdmin(HttpServletRequest request) {
        AuthUser user = requireUser(request);
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        return user;
    }
}
