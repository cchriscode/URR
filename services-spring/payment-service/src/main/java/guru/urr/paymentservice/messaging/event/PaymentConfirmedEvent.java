package guru.urr.paymentservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentConfirmedEvent(
    String type,
    UUID paymentId,
    String orderId,
    String userId,
    UUID reservationId,
    UUID referenceId,
    String paymentType,
    int amount,
    String paymentMethod,
    Instant timestamp
) {
    public PaymentConfirmedEvent(UUID paymentId, String orderId, String userId,
                                  UUID reservationId, UUID referenceId, String paymentType,
                                  int amount, String paymentMethod, Instant timestamp) {
        this("PAYMENT_CONFIRMED", paymentId, orderId, userId, reservationId,
             referenceId, paymentType, amount, paymentMethod, timestamp);
    }
}
