package com.tiketi.ticketservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record MembershipActivatedEvent(
    String type,
    UUID membershipId,
    String userId,
    UUID artistId,
    Instant timestamp
) {
    public MembershipActivatedEvent(UUID membershipId, String userId, UUID artistId,
                                     Instant timestamp) {
        this("MEMBERSHIP_ACTIVATED", membershipId, userId, artistId, timestamp);
    }
}
