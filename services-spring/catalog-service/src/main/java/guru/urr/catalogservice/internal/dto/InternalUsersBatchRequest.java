package guru.urr.catalogservice.internal.dto;

import java.util.List;

public record InternalUsersBatchRequest(List<String> userIds) {
}
