package com.tiketi.catalogservice.shared.client;

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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TicketInternalClient {

    private static final Logger log = LoggerFactory.getLogger(TicketInternalClient.class);

    private final RestClient restClient;
    private final String internalApiToken;

    public TicketInternalClient(
        @Value("${internal.ticket-service-url:http://localhost:3002}") String ticketServiceUrl,
        @Value("${internal.api-token}") String internalApiToken
    ) {
        var requestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10)));
        this.restClient = RestClient.builder().baseUrl(ticketServiceUrl).requestFactory(requestFactory).build();
        this.internalApiToken = internalApiToken;
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "generateSeatsFallback")
    // No @Retry: POST is not idempotent — retry could cause duplicate seat creation
    public int generateSeats(UUID eventId, UUID layoutId) {
        Map<String, Object> response = restClient.post()
            .uri("/internal/seats/generate/{eventId}/{layoutId}", eventId, layoutId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (response != null && response.get("seatsCreated") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "countSeatsFallback")
    @Retry(name = "internalService")
    public int countSeats(UUID eventId) {
        Map<String, Object> response = restClient.get()
            .uri("/internal/seats/count/{eventId}", eventId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (response != null && response.get("count") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "deleteSeatsFallback")
    @Retry(name = "internalService")
    public int deleteSeats(UUID eventId) {
        Map<String, Object> response = restClient.delete()
            .uri("/internal/seats/{eventId}", eventId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (response != null && response.get("deleted") instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    @SuppressWarnings("unused")
    private int generateSeatsFallback(UUID eventId, UUID layoutId, Throwable t) {
        log.error("Circuit breaker: generateSeats failed for event {}: {}", eventId, t.getMessage());
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Seat generation service unavailable");
    }

    @SuppressWarnings("unused")
    private int countSeatsFallback(UUID eventId, Throwable t) {
        log.warn("Circuit breaker: countSeats failed for event {}: {}", eventId, t.getMessage());
        return 0;
    }

    @SuppressWarnings("unused")
    private int deleteSeatsFallback(UUID eventId, Throwable t) {
        log.error("Circuit breaker: deleteSeats failed for event {}: {}", eventId, t.getMessage());
        throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Seat management service unavailable");
    }
}
