package com.tiketi.ticketservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReservationItemRequest(
    @NotNull UUID ticketTypeId,
    @NotNull @Min(1) Integer quantity
) {
}
