package guru.urr.catalogservice.shared.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
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

    // --- Ticket Types ---

    @CircuitBreaker(name = "internalService", fallbackMethod = "ticketTypesFallback")
    @Retry(name = "internalService")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTicketTypesByEvent(UUID eventId) {
        Map<String, Object> response = restClient.get()
            .uri("/internal/ticket-types?eventId={eventId}", eventId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (response != null && response.get("ticketTypes") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @Retry(name = "internalService")
    public Map<String, Object> getTicketTypeAvailability(UUID ticketTypeId) {
        return restClient.get()
            .uri("/internal/ticket-types/{id}/availability", ticketTypeId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> createTicketType(UUID eventId, String name, int price, int totalQuantity, String description) {
        Map<String, Object> body = Map.of(
            "eventId", eventId.toString(), "name", name, "price", price,
            "totalQuantity", totalQuantity, "description", description != null ? description : "");
        return restClient.post()
            .uri("/internal/ticket-types")
            .header("Authorization", "Bearer " + internalApiToken)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> updateTicketType(UUID id, String name, int price, int totalQuantity, String description) {
        Map<String, Object> body = Map.of(
            "name", name, "price", price, "totalQuantity", totalQuantity,
            "description", description != null ? description : "");
        return restClient.put()
            .uri("/internal/ticket-types/{id}", id)
            .header("Authorization", "Bearer " + internalApiToken)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    // --- Admin / Reservation Data ---

    @CircuitBreaker(name = "internalService", fallbackMethod = "reservationStatsFallback")
    @Retry(name = "internalService")
    public Map<String, Object> getReservationStats() {
        return restClient.get()
            .uri("/internal/admin/reservation-stats")
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    @Retry(name = "internalService")
    public List<Map<String, Object>> getRecentReservations() {
        Map<String, Object> response = restClient.get()
            .uri("/internal/admin/recent-reservations")
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (response != null && response.get("reservations") instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @Retry(name = "internalService")
    public Map<String, Object> listReservations(int page, int limit, String status) {
        String uri = status != null && !status.isBlank()
            ? "/internal/admin/reservations?page={page}&limit={limit}&status={status}"
            : "/internal/admin/reservations?page={page}&limit={limit}";
        return restClient.get()
            .uri(uri, page, limit, status)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    public Map<String, Object> updateReservationStatus(UUID id, String status, String paymentStatus) {
        Map<String, String> body = new HashMap<>();
        if (status != null) body.put("status", status);
        if (paymentStatus != null) body.put("paymentStatus", paymentStatus);
        return restClient.patch()
            .uri("/internal/admin/reservations/{id}/status", id)
            .header("Authorization", "Bearer " + internalApiToken)
            .body(body)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    public int cancelReservationsByEvent(UUID eventId) {
        Map<String, Object> response = restClient.post()
            .uri("/internal/admin/reservations/cancel-by-event/{eventId}", eventId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (response != null && response.get("cancelledCount") instanceof Number n) return n.intValue();
        return 0;
    }

    public int cancelAllReservationsByEvent(UUID eventId) {
        Map<String, Object> response = restClient.post()
            .uri("/internal/admin/reservations/cancel-all-by-event/{eventId}", eventId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (response != null && response.get("cancelledCount") instanceof Number n) return n.intValue();
        return 0;
    }

    @Retry(name = "internalService")
    public Map<String, Object> getSeatLayouts() {
        return restClient.get()
            .uri("/internal/admin/seat-layouts")
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }

    @Retry(name = "internalService")
    public int getActiveSeatReservationCount(UUID eventId) {
        Map<String, Object> response = restClient.get()
            .uri("/internal/admin/active-seat-reservation-count?eventId={eventId}", eventId)
            .header("Authorization", "Bearer " + internalApiToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
        if (response != null && response.get("count") instanceof Number n) return n.intValue();
        return 0;
    }

    // --- Fallbacks ---

    @SuppressWarnings("unused")
    private List<Map<String, Object>> ticketTypesFallback(UUID eventId, Throwable t) {
        log.warn("Circuit breaker: getTicketTypesByEvent failed for {}: {}", eventId, t.getMessage());
        return List.of();
    }

    @SuppressWarnings("unused")
    private Map<String, Object> reservationStatsFallback(Throwable t) {
        log.warn("Circuit breaker: getReservationStats failed: {}", t.getMessage());
        return Map.of("totalReservations", 0, "totalRevenue", 0, "todayReservations", 0);
    }
}
