package guru.urr.paymentservice.security;

public record AuthUser(String userId, String email, String role) {
}
