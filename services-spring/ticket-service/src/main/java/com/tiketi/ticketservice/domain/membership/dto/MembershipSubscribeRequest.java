package com.tiketi.ticketservice.domain.membership.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MembershipSubscribeRequest(
    @NotNull UUID artistId
) {}
