package com.tiketi.paymentservice.client;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class TicketInternalClient {

    private final RestClient restClient;
    private final String internalToken;

    public TicketInternalClient(
        @Value("${TICKET_SERVICE_URL:http://localhost:3002}") String ticketServiceUrl,
        @Value("${INTERNAL_API_TOKEN:dev-internal-token-change-me}") String internalToken
    ) {
        this.restClient = RestClient.builder().baseUrl(ticketServiceUrl).build();
        this.internalToken = internalToken;
    }

    public Map<String, Object> validateReservation(UUID reservationId, String userId) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/reservations/{id}/validate").queryParam("userId", userId).build(reservationId))
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Reservation validation failed");
        }
    }

    public void confirmReservation(UUID reservationId, String paymentMethod) {
        try {
            restClient.post()
                .uri("/internal/reservations/{id}/confirm", reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + internalToken)
                .body(Map.of("paymentMethod", paymentMethod))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Reservation confirm failed");
        }
    }

    public void refundReservation(UUID reservationId) {
        try {
            restClient.post()
                .uri("/internal/reservations/{id}/refund", reservationId)
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Reservation refund failed");
        }
    }

    // === Transfer APIs ===

    public Map<String, Object> validateTransfer(UUID transferId, String userId) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/transfers/{id}/validate").queryParam("userId", userId).build(transferId))
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Transfer validation failed: " + ex.getMessage());
        }
    }

    public void completeTransfer(UUID transferId, String buyerId, String paymentMethod) {
        try {
            restClient.post()
                .uri("/internal/transfers/{id}/complete", transferId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + internalToken)
                .body(Map.of("buyerId", buyerId, "paymentMethod", paymentMethod))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Transfer complete failed: " + ex.getMessage());
        }
    }

    // === Membership APIs ===

    public Map<String, Object> validateMembership(UUID membershipId, String userId) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/memberships/{id}/validate").queryParam("userId", userId).build(membershipId))
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .body(Map.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Membership validation failed: " + ex.getMessage());
        }
    }

    public void activateMembership(UUID membershipId) {
        try {
            restClient.post()
                .uri("/internal/memberships/{id}/activate", membershipId)
                .header("Authorization", "Bearer " + internalToken)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "Membership activation failed: " + ex.getMessage());
        }
    }
}
