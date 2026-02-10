package com.tiketi.authservice.controller;

import com.tiketi.authservice.dto.InternalUsersRequest;
import com.tiketi.authservice.security.InternalTokenValidator;
import com.tiketi.authservice.service.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final AuthService authService;
    private final InternalTokenValidator internalTokenValidator;

    public InternalUserController(AuthService authService, InternalTokenValidator internalTokenValidator) {
        this.authService = authService;
        this.internalTokenValidator = internalTokenValidator;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> byId(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "x-internal-token", required = false) String xInternalToken
    ) {
        if (!internalTokenValidator.isValid(authorization, xInternalToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Internal token required"));
        }
        return ResponseEntity.ok(Map.of("user", authService.findUserById(id)));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> batch(
        @Valid @RequestBody InternalUsersRequest request,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestHeader(value = "x-internal-token", required = false) String xInternalToken
    ) {
        if (!internalTokenValidator.isValid(authorization, xInternalToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Internal token required"));
        }

        List<UUID> ids;
        try {
            ids = request.userIds().stream().map(UUID::fromString).toList();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid userIds"));
        }
        return ResponseEntity.ok(Map.of("users", authService.findUsersByIds(ids)));
    }
}
