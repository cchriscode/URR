package com.tiketi.authservice.dto;

public record MeResponse(MeUser user) {
    public record MeUser(
        String id,
        String email,
        String name,
        String role,
        String createdAt
    ) {}
}
