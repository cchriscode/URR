package guru.urr.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyTokenRequest(@NotBlank String token) {
}
