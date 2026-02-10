package com.tiketi.ticketservice.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MembershipSubscribeRequest(
    @NotNull UUID artistId
) {}
