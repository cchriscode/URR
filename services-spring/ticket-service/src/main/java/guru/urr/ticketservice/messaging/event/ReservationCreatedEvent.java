package guru.urr.ticketservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedEvent(
    String type,
    UUID sagaId,
    UUID reservationId,
    String userId,
    UUID eventId,
    int totalAmount,
    Instant timestamp
) {
    public ReservationCreatedEvent(UUID reservationId, String userId, UUID eventId,
                                    int totalAmount, Instant timestamp) {
        this("RESERVATION_CREATED", null, reservationId, userId, eventId, totalAmount, timestamp);
    }
}
