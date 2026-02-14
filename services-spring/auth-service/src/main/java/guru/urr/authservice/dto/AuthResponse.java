package guru.urr.authservice.dto;

public record AuthResponse(
    String message,
    String token,
    String refreshToken,
    UserPayload user
) {
}
