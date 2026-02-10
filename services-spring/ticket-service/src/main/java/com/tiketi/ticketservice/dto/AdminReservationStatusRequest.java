package com.tiketi.ticketservice.dto;

public record AdminReservationStatusRequest(
    String status,
    String paymentStatus
) {
}
