package com.tiketi.ticketservice.messaging.event;

import java.time.Instant;
import java.util.UUID;

public record TransferCompletedEvent(
    String type,
    UUID transferId,
    UUID reservationId,
    String sellerId,
    String buyerId,
    int totalPrice,
    Instant timestamp
) {
    public TransferCompletedEvent(UUID transferId, UUID reservationId, String sellerId,
                                   String buyerId, int totalPrice, Instant timestamp) {
        this("TRANSFER_COMPLETED", transferId, reservationId, sellerId, buyerId,
             totalPrice, timestamp);
    }
}
