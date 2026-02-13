package com.tiketi.authservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.tiketi.authservice.domain.UserEntity;
import com.tiketi.authservice.domain.UserRole;
import com.tiketi.authservice.dto.AuthResponse;
import com.tiketi.authservice.dto.LoginRequest;
import com.tiketi.authservice.dto.RegisterRequest;
import com.tiketi.authservice.exception.ApiException;
import com.tiketi.authservice.repository.UserRepository;
import com.tiketi.authservice.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService, "");
    }

    @Test
    void register_success() {
        RegisterRequest request = new RegisterRequest("test@example.com", "password123", "Test User", null);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");

        UserEntity savedUser = new UserEntity();
        savedUser.setId(UUID.randomUUID());
        savedUser.setEmail("test@example.com");
        savedUser.setName("Test User");
        savedUser.setRole(UserRole.user);

        when(userRepository.save(any(UserEntity.class))).thenReturn(savedUser);
        when(jwtService.generateToken(savedUser)).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertEquals("Registration completed", response.message());
        assertEquals("jwt-token", response.token());
        assertNotNull(response.user());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest("existing@example.com", "password123", "User", null);

        UserEntity existingUser = new UserEntity();
        existingUser.setEmail("existing@example.com");
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existingUser));

        assertThrows(ApiException.class, () -> authService.register(request));
    }

    @Test
    void login_success() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setPasswordHash("$2a$encoded");
        user.setName("Test User");
        user.setRole(UserRole.user);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$encoded")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertEquals("Login successful", response.message());
        assertEquals("jwt-token", response.token());
    }

    @Test
    void login_wrongPassword_throws() {
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

        UserEntity user = new UserEntity();
        user.setEmail("test@example.com");
        user.setPasswordHash("$2a$encoded");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "$2a$encoded")).thenReturn(false);

        assertThrows(ApiException.class, () -> authService.login(request));
    }

    @Test
    void login_userNotFound_throws() {
        LoginRequest request = new LoginRequest("unknown@example.com", "password123");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> authService.login(request));
    }
}
