package guru.urr.catalogservice.domain.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminTicketTypeRequest(
    @NotBlank String name,
    @NotNull @Min(0) Integer price,
    @NotNull @Min(1) Integer totalQuantity,
    String description
) {
}
