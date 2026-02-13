package com.tiketi.catalogservice.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminEventRequest(
    @NotBlank String title,
    String description,
    @NotBlank String venue,
    String address,
    @NotNull OffsetDateTime eventDate,
    @NotNull OffsetDateTime saleStartDate,
    @NotNull OffsetDateTime saleEndDate,
    String posterImageUrl,
    String artistName,
    UUID seatLayoutId,
    @JsonProperty("ticketTypes") @Valid List<AdminTicketTypeRequest> ticketTypes
) {
}
