package guru.urr.communityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PostCreateRequest(
    @NotBlank String title,
    @NotBlank String content,
    @NotNull @JsonProperty("artist_id") UUID artistId
) {}
