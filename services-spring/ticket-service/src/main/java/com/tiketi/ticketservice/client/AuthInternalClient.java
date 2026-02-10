package com.tiketi.ticketservice.client;

import com.tiketi.ticketservice.dto.InternalUsersBatchRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuthInternalClient {

    private final RestClient restClient;
    private final String internalApiToken;

    public AuthInternalClient(
        @Value("${internal.auth-service-url:http://localhost:3005}") String authServiceUrl,
        @Value("${INTERNAL_API_TOKEN:dev-internal-token-change-me}") String internalApiToken
    ) {
        this.restClient = RestClient.builder().baseUrl(authServiceUrl).build();
        this.internalApiToken = internalApiToken;
    }

    public Map<UUID, Map<String, Object>> findUsersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        try {
            Map<String, Object> response = restClient.post()
                .uri("/internal/users/batch")
                .header("Authorization", "Bearer " + internalApiToken)
                .body(new InternalUsersBatchRequest(ids.stream().map(UUID::toString).toList()))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

            if (response == null || !(response.get("users") instanceof List<?> users)) {
                return Map.of();
            }

            Map<UUID, Map<String, Object>> userMap = new HashMap<>();
            for (Object item : users) {
                if (item instanceof Map<?, ?> raw && raw.get("id") != null) {
                    UUID id = UUID.fromString(String.valueOf(raw.get("id")));
                    userMap.put(id, castToStringObjectMap(raw));
                }
            }
            return userMap;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
