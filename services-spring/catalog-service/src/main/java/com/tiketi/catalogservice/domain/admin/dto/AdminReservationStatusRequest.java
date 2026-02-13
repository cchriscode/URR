package com.tiketi.catalogservice.domain.admin.dto;

public record AdminReservationStatusRequest(
    String status,
    String paymentStatus
) {
}
