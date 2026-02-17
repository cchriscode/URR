package guru.urr.authservice.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record InternalUsersRequest(@NotEmpty List<String> userIds) {
}
