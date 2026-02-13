package com.tiketi.ticketservice.internal.dto;

import java.util.List;
import java.util.UUID;

public record InternalSeatReserveRequest(
    UUID eventId,
    String userId,
    List<UUID> seatIds,
    String entryToken
) {}
