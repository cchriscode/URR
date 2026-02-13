package com.tiketi.communityservice.shared.security;

public record AuthUser(String userId, String email, String role) {
    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
}
