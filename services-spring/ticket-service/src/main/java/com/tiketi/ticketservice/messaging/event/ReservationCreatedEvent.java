package com.tiketi.ticketservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedEvent(
    String type,
    UUID reservationId,
    String userId,
    UUID eventId,
    int totalAmount,
    Instant timestamp
) {
    public ReservationCreatedEvent(UUID reservationId, String userId, UUID eventId,
                                    int totalAmount, Instant timestamp) {
        this("RESERVATION_CREATED", reservationId, userId, eventId, totalAmount, timestamp);
    }
}
