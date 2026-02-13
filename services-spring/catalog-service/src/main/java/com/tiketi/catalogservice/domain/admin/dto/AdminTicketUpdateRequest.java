package com.tiketi.catalogservice.domain.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminTicketUpdateRequest(
    @NotBlank String name,
    @NotNull @Min(0) Integer price,
    @NotNull @Min(1) Integer totalQuantity,
    String description
) {
}
