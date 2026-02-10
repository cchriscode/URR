package com.tiketi.ticketservice.security;

public record AuthUser(String userId, String email, String role) {
    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
}
