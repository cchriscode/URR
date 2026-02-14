package guru.urr.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfirmPaymentRequest(
    @NotBlank String paymentKey,
    @NotBlank String orderId,
    @NotNull @Min(1) Integer amount
) {
}
