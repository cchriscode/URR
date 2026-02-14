package com.tiketi.communityservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateRequest(
    @NotBlank String content
) {}
