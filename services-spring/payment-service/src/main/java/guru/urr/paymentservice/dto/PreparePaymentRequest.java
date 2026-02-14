package guru.urr.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PreparePaymentRequest(
    UUID reservationId,
    @NotNull @Min(1) Integer amount,
    String paymentType,
    UUID referenceId
) {
}
