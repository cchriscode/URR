package guru.urr.catalogservice.shared.client;

import guru.urr.catalogservice.internal.dto.InternalUsersBatchRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuthInternalClient {

    private static final Logger log = LoggerFactory.getLogger(AuthInternalClient.class);

    private final RestClient restClient;
    private final String internalApiToken;

    public AuthInternalClient(
        @Value("${internal.auth-service-url:http://localhost:3005}") String authServiceUrl,
        @Value("${internal.api-token}") String internalApiToken
    ) {
        var requestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10)));
        this.restClient = RestClient.builder().baseUrl(authServiceUrl).requestFactory(requestFactory).build();
        this.internalApiToken = internalApiToken;
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "findUsersByIdsFallback")
    @Retry(name = "internalService")
    public Map<UUID, Map<String, Object>> findUsersByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

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
    }

    @SuppressWarnings("unused")
    private Map<UUID, Map<String, Object>> findUsersByIdsFallback(Collection<UUID> ids, Throwable t) {
        log.warn("Circuit breaker: findUsersByIds failed: {}", t.getMessage());
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
