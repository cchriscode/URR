package guru.urr.ticketservice.internal.dto;

import java.util.List;
import java.util.UUID;

public record InternalCreateReservationRequest(
    UUID eventId,
    String userId,
    List<Item> items,
    String entryToken
) {
    public record Item(
        UUID ticketTypeId,
        Integer quantity
    ) {}
}
