package com.tiketi.ticketservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationConfirmedEvent(
    String type,
    UUID sagaId,
    UUID reservationId,
    String userId,
    UUID eventId,
    int totalAmount,
    String paymentMethod,
    Instant timestamp
) {
    public ReservationConfirmedEvent(UUID reservationId, String userId, UUID eventId,
                                      int totalAmount, String paymentMethod, Instant timestamp) {
        this("RESERVATION_CONFIRMED", null, reservationId, userId, eventId, totalAmount,
             paymentMethod, timestamp);
    }
}
