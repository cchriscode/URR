package guru.urr.queueservice.shared.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TicketInternalClient {

    private static final Logger log = LoggerFactory.getLogger(TicketInternalClient.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int READ_TIMEOUT_SECONDS = 10;

    private final RestClient restClient;
    private final String internalApiToken;

    public TicketInternalClient(
        @Value("${internal.catalog-service-url:http://localhost:3009}") String catalogServiceUrl,
        @Value("${internal.api-token}") String internalApiToken
    ) {
        var requestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .withReadTimeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS)));
        this.restClient = RestClient.builder()
            .baseUrl(catalogServiceUrl)
            .requestFactory(requestFactory)
            .build();
        this.internalApiToken = internalApiToken;
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "getEventQueueInfoFallback")
    @Retry(name = "internalService")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getEventQueueInfo(UUID eventId) {
        Map<String, Object> response = restClient.get()
            .uri("/internal/events/{eventId}/queue-info", eventId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(Map.class);
        return response != null ? response : Map.of("title", "Unknown");
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getEventQueueInfoFallback(UUID eventId, Throwable t) {
        log.warn("Circuit breaker: getEventQueueInfo failed for event {}: {}", eventId, t.getMessage());
        return Map.of("title", "Unknown");
    }
}
