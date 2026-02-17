package guru.urr.ticketservice.domain.reservation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record SeatReserveRequest(
    @NotNull UUID eventId,
    @NotNull List<UUID> seatIds,
    String idempotencyKey
) {
}
