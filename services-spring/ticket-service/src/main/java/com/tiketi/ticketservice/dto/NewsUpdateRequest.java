package com.tiketi.ticketservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record NewsUpdateRequest(
    @NotBlank String title,
    @NotBlank String content,
    @JsonProperty("is_pinned") Boolean isPinned
) {
}
