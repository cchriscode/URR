package com.tiketi.communityservice.dto;

import jakarta.validation.constraints.NotBlank;

public record PostUpdateRequest(
    @NotBlank String title,
    @NotBlank String content
) {}
