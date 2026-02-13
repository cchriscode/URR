package com.tiketi.communityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record NewsCreateRequest(
    @NotBlank String title,
    @NotBlank String content,
    @NotBlank String author,
    @JsonProperty("author_id") UUID authorId,
    @JsonProperty("is_pinned") Boolean isPinned
) {
}
