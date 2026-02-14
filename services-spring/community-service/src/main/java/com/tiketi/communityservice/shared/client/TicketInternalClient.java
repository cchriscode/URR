package com.tiketi.communityservice.shared.client;

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

    private final RestClient restClient;
    private final String internalApiToken;

    public TicketInternalClient(
            @Value("${internal.ticket-service-url}") String ticketServiceUrl,
            @Value("${internal.api-token}") String internalApiToken) {
        var requestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10)));
        this.restClient = RestClient.builder().baseUrl(ticketServiceUrl).requestFactory(requestFactory).build();
        this.internalApiToken = internalApiToken;
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "awardMembershipPointsFallback")
    public void awardMembershipPoints(String userId, String actionType, int points,
                                       String description, UUID referenceId) {
        awardMembershipPoints(userId, null, actionType, points, description, referenceId);
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "awardMembershipPointsArtistFallback")
    // No @Retry: POST is not idempotent — retry could cause duplicate point awards
    public void awardMembershipPoints(String userId, UUID artistId, String actionType,
                                       int points, String description, UUID referenceId) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("userId", userId);
        body.put("actionType", actionType);
        body.put("points", points);
        body.put("description", description);
        body.put("referenceId", referenceId);
        if (artistId != null) {
            body.put("artistId", artistId);
        }
        restClient.post()
            .uri("/internal/memberships/award-points")
            .header("Authorization", "Bearer " + internalApiToken)
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private void awardMembershipPointsFallback(String userId, String actionType, int points,
                                                String description, UUID referenceId, Throwable t) {
        log.warn("Circuit breaker: awardMembershipPoints failed for user {}: {}", userId, t.getMessage());
    }

    @SuppressWarnings("unused")
    private void awardMembershipPointsArtistFallback(String userId, UUID artistId, String actionType,
                                                      int points, String description, UUID referenceId,
                                                      Throwable t) {
        log.warn("Circuit breaker: awardMembershipPoints failed for user {} artist {}: {}",
                userId, artistId, t.getMessage());
    }
}
