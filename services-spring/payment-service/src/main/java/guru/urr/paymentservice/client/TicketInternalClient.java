package guru.urr.paymentservice.client;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TicketInternalClient {

    private static final Logger log = LoggerFactory.getLogger(TicketInternalClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public TicketInternalClient(
        @Value("${TICKET_SERVICE_URL:http://localhost:3002}") String ticketServiceUrl,
        @Value("${INTERNAL_API_TOKEN}") String internalToken
    ) {
        var requestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10)));
        this.restClient = RestClient.builder().baseUrl(ticketServiceUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "validateReservationFallback")
    @Retry(name = "internalService")
    public Map<String, Object> validateReservation(UUID reservationId, String userId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/reservations/{id}/validate").queryParam("userId", userId).build(reservationId))
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .body(Map.class);
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "validateTransferFallback")
    @Retry(name = "internalService")
    public Map<String, Object> validateTransfer(UUID transferId, String userId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/transfers/{id}/validate").queryParam("userId", userId).build(transferId))
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .body(Map.class);
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "validateMembershipFallback")
    @Retry(name = "internalService")
    public Map<String, Object> validateMembership(UUID membershipId, String userId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/memberships/{id}/validate").queryParam("userId", userId).build(membershipId))
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .body(Map.class);
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "confirmReservationFallback")
    @Retry(name = "internalService")
    public void confirmReservation(UUID reservationId, String paymentMethod) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/internal/reservations/{id}/confirm").build(reservationId))
                .header("Authorization", "Bearer " + internalToken)
                .body(Map.of("paymentMethod", paymentMethod))
                .retrieve()
                .toBodilessEntity();
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "confirmTransferFallback")
    @Retry(name = "internalService")
    public void confirmTransfer(UUID transferId, String buyerId, String paymentMethod) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/internal/transfers/{id}/complete").build(transferId))
                .header("Authorization", "Bearer " + internalToken)
                .body(Map.of("buyerId", buyerId, "paymentMethod", paymentMethod))
                .retrieve()
                .toBodilessEntity();
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "activateMembershipFallback")
    @Retry(name = "internalService")
    public void activateMembership(UUID membershipId) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/internal/memberships/{id}/activate").build(membershipId))
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private void confirmReservationFallback(UUID reservationId, String paymentMethod, Throwable t) {
        log.error("Circuit breaker: confirmReservation failed for reservation {}: {}", reservationId, t.getMessage());
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Ticket service unavailable for confirmation: " + t.getMessage());
    }

    @SuppressWarnings("unused")
    private Map<String, Object> validateReservationFallback(UUID reservationId, String userId, Throwable t) {
        log.error("Circuit breaker: validateReservation failed for reservation {}: {}", reservationId, t.getMessage());
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ticket service unavailable: " + t.getMessage());
    }

    @SuppressWarnings("unused")
    private Map<String, Object> validateTransferFallback(UUID transferId, String userId, Throwable t) {
        log.error("Circuit breaker: validateTransfer failed for transfer {}: {}", transferId, t.getMessage());
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ticket service unavailable: " + t.getMessage());
    }

    @SuppressWarnings("unused")
    private Map<String, Object> validateMembershipFallback(UUID membershipId, String userId, Throwable t) {
        log.error("Circuit breaker: validateMembership failed for membership {}: {}", membershipId, t.getMessage());
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ticket service unavailable: " + t.getMessage());
    }

    @SuppressWarnings("unused")
    private void confirmTransferFallback(UUID transferId, String buyerId, String paymentMethod, Throwable t) {
        log.error("Circuit breaker: confirmTransfer failed for transfer {}: {}", transferId, t.getMessage());
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Ticket service unavailable for transfer confirmation: " + t.getMessage());
    }

    @SuppressWarnings("unused")
    private void activateMembershipFallback(UUID membershipId, Throwable t) {
        log.error("Circuit breaker: activateMembership failed for membership {}: {}", membershipId, t.getMessage());
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "Ticket service unavailable for membership activation: " + t.getMessage());
    }
}
