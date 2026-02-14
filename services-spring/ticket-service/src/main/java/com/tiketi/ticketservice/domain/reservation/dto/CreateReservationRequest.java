package com.tiketi.ticketservice.domain.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(
    @NotNull UUID eventId,
    @NotNull @Valid List<ReservationItemRequest> items,
    String idempotencyKey
) {
}
