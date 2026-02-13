package com.tiketi.paymentservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedEvent(
    String type,
    UUID paymentId,
    String orderId,
    String userId,
    UUID reservationId,
    UUID referenceId,
    String paymentType,
    int amount,
    String reason,
    Instant timestamp
) {
    public PaymentRefundedEvent(UUID paymentId, String orderId, String userId,
                                 UUID reservationId, UUID referenceId, String paymentType,
                                 int amount, String reason, Instant timestamp) {
        this("PAYMENT_REFUNDED", paymentId, orderId, userId, reservationId,
             referenceId, paymentType, amount, reason, timestamp);
    }
}
