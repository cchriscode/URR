package guru.urr.paymentservice.service;

import guru.urr.paymentservice.client.TicketInternalClient;
import guru.urr.paymentservice.messaging.PaymentEventProducer;
import guru.urr.paymentservice.messaging.event.PaymentConfirmedEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentTypeDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PaymentTypeDispatcher.class);

    private final TicketInternalClient ticketInternalClient;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentTypeDispatcher(TicketInternalClient ticketInternalClient, PaymentEventProducer paymentEventProducer) {
        this.ticketInternalClient = ticketInternalClient;
        this.paymentEventProducer = paymentEventProducer;
    }

    public void completeByType(String paymentType, Map<String, Object> payment, String userId, String paymentMethod) {
        UUID reservationId = asUuidNullable(payment.get("reservation_id"));
        UUID referenceId = asUuidNullable(payment.get("reference_id"));
        UUID paymentId = asUuidNullable(payment.get("id"));

        int amount = 0;
        Object amountObj = payment.get("amount");
        if (amountObj instanceof Number n) amount = n.intValue();

        String orderId = payment.get("order_id") != null ? String.valueOf(payment.get("order_id")) : null;

        // Synchronous confirmation via internal API (primary path)
        try {
            switch (paymentType) {
                case "transfer" -> {
                    if (referenceId != null) ticketInternalClient.confirmTransfer(referenceId, userId, paymentMethod);
                }
                case "membership" -> {
                    if (referenceId != null) ticketInternalClient.activateMembership(referenceId);
                }
                default -> {
                    if (reservationId != null) ticketInternalClient.confirmReservation(reservationId, paymentMethod);
                }
            }
        } catch (Exception e) {
            log.warn("Synchronous confirmation failed for {} {}, falling back to Kafka: {}",
                paymentType, reservationId != null ? reservationId : referenceId, e.getMessage());
        }

        // Kafka event (secondary: stats, notifications, eventual consistency fallback)
        paymentEventProducer.publish(new PaymentConfirmedEvent(
            paymentId, orderId, userId, reservationId, referenceId,
            paymentType, amount, paymentMethod, Instant.now()));
    }

    private UUID asUuidNullable(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
        return null;
    }
}
