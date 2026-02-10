package com.tiketi.authservice.dto;

import com.tiketi.authservice.domain.UserEntity;

public record UserPayload(
    String id,
    String userId,
    String email,
    String name,
    String role
) {
    public static UserPayload from(UserEntity user) {
        String id = user.getId().toString();
        return new UserPayload(id, id, user.getEmail(), user.getName(), user.getRole().name());
    }
}
