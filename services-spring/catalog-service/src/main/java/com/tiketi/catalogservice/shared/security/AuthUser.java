package com.tiketi.catalogservice.shared.security;

public record AuthUser(String userId, String email, String role) {
    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
}
