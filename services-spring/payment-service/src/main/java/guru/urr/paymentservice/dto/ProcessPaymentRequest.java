package guru.urr.paymentservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ProcessPaymentRequest(
    UUID reservationId,
    @NotBlank String paymentMethod,
    String paymentType,
    UUID referenceId
) {
}
