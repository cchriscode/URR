package guru.urr.ticketservice.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Typed representation of incoming payment events from payment-service.
 * Replaces raw Map&lt;String, Object&gt; for type safety and IDE support.
 *
 * NOTE: Other consumers (stats-service, etc.) should be migrated similarly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEvent(
    String type,
    String sagaId,
    String paymentId,
    String reservationId,
    String referenceId,
    String userId,
    String paymentType,
    String paymentMethod,
    Long amount,
    String status,
    String reason
) {}
