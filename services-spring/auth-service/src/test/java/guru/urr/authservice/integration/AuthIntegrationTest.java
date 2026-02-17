package guru.urr.authservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import guru.urr.authservice.dto.AuthResponse;
import guru.urr.authservice.dto.LoginRequest;
import guru.urr.authservice.dto.MeResponse;
import guru.urr.authservice.dto.RegisterRequest;
import guru.urr.authservice.exception.ApiException;
import guru.urr.authservice.repository.UserRepository;
import guru.urr.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class AuthIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void register_thenLogin_fullFlow() {
        // Register
        RegisterRequest registerReq = new RegisterRequest(
                "integration@test.com", "password123", "Test User", "010-1234-5678");

        AuthResponse registerResp = authService.register(registerReq);

        assertEquals("Registration completed", registerResp.message());
        assertNotNull(registerResp.token());
        assertNotNull(registerResp.refreshToken());
        assertEquals("integration@test.com", registerResp.user().email());
        assertEquals("Test User", registerResp.user().name());

        // Login with same credentials
        LoginRequest loginReq = new LoginRequest("integration@test.com", "password123");

        AuthResponse loginResp = authService.login(loginReq);

        assertEquals("Login successful", loginResp.message());
        assertNotNull(loginResp.token());
        assertEquals("integration@test.com", loginResp.user().email());
    }

    @Test
    void register_thenMe_returnsUserInfo() {
        RegisterRequest registerReq = new RegisterRequest(
                "me@test.com", "password123", "Me User", null);

        AuthResponse registerResp = authService.register(registerReq);

        // Use the token to fetch user info
        MeResponse meResp = authService.me("Bearer " + registerResp.token());

        assertEquals("me@test.com", meResp.user().email());
        assertEquals("Me User", meResp.user().name());
        assertEquals("user", meResp.user().role());
    }

    @Test
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest(
                "duplicate@test.com", "password123", "User One", null);

        authService.register(request);

        RegisterRequest duplicate = new RegisterRequest(
                "duplicate@test.com", "password456", "User Two", null);

        assertThrows(ApiException.class, () -> authService.register(duplicate));
    }

    @Test
    void login_wrongPassword_throws() {
        RegisterRequest registerReq = new RegisterRequest(
                "wrong@test.com", "correctpassword", "User", null);
        authService.register(registerReq);

        LoginRequest loginReq = new LoginRequest("wrong@test.com", "wrongpassword");

        assertThrows(ApiException.class, () -> authService.login(loginReq));
    }

    @Test
    void login_nonExistentUser_throws() {
        LoginRequest loginReq = new LoginRequest("nobody@test.com", "password123");

        assertThrows(ApiException.class, () -> authService.login(loginReq));
    }

    @Test
    void register_thenRefreshToken_rotatesToken() {
        RegisterRequest registerReq = new RegisterRequest(
                "refresh@test.com", "password123", "Refresh User", null);

        AuthResponse registerResp = authService.register(registerReq);
        String originalRefreshToken = registerResp.refreshToken();

        // Refresh token
        AuthResponse refreshResp = authService.refreshToken(originalRefreshToken);

        assertEquals("Token refreshed", refreshResp.message());
        assertNotNull(refreshResp.token());
        assertNotNull(refreshResp.refreshToken());
        // New refresh token should be different from original
        assertNotEquals(originalRefreshToken, refreshResp.refreshToken());
    }

    @Test
    void refreshToken_reuseDetected_revokesFamily() {
        RegisterRequest registerReq = new RegisterRequest(
                "reuse@test.com", "password123", "Reuse User", null);

        AuthResponse registerResp = authService.register(registerReq);
        String originalRefreshToken = registerResp.refreshToken();

        // First refresh succeeds
        authService.refreshToken(originalRefreshToken);

        // Second refresh with same token should fail (reuse detection)
        assertThrows(ApiException.class, () -> authService.refreshToken(originalRefreshToken));
    }
}
