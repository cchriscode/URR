package com.tiketi.ticketservice.internal.dto;

import java.util.UUID;

public record AwardPointsRequest(
    String userId,
    String actionType,
    int points,
    String description,
    UUID referenceId
) {}
