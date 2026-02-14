package guru.urr.ticketservice.domain.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReservationItemRequest(
    @NotNull UUID ticketTypeId,
    @NotNull @Min(1) @Max(10) Integer quantity
) {
}
