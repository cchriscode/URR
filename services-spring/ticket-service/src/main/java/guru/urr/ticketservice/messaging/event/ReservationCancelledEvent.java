package guru.urr.ticketservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationCancelledEvent(
    String type,
    UUID sagaId,
    UUID reservationId,
    String userId,
    UUID eventId,
    String reason,
    Instant timestamp
) {
    public ReservationCancelledEvent(UUID reservationId, String userId, UUID eventId,
                                      String reason, Instant timestamp) {
        this("RESERVATION_CANCELLED", null, reservationId, userId, eventId, reason, timestamp);
    }
}
