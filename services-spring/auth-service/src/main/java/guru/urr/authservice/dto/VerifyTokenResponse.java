package guru.urr.authservice.dto;

public record VerifyTokenResponse(
    boolean valid,
    VerifiedUser user
) {
    public record VerifiedUser(
        String userId,
        String email,
        String name,
        String role
    ) {}
}
