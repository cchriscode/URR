package com.tiketi.ticketservice.messaging;

import com.tiketi.ticketservice.messaging.event.MembershipActivatedEvent;
import com.tiketi.ticketservice.messaging.event.ReservationCancelledEvent;
import com.tiketi.ticketservice.messaging.event.ReservationConfirmedEvent;
import com.tiketi.ticketservice.messaging.event.TransferCompletedEvent;
import com.tiketi.ticketservice.domain.membership.service.MembershipService;
import com.tiketi.ticketservice.domain.reservation.service.ReservationService;
import com.tiketi.ticketservice.domain.transfer.service.TransferService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final ReservationService reservationService;
    private final TransferService transferService;
    private final MembershipService membershipService;
    private final TicketEventProducer ticketEventProducer;
    private final JdbcTemplate jdbcTemplate;

    public PaymentEventConsumer(ReservationService reservationService,
                                TransferService transferService,
                                MembershipService membershipService,
                                TicketEventProducer ticketEventProducer,
                                JdbcTemplate jdbcTemplate) {
        this.reservationService = reservationService;
        this.transferService = transferService;
        this.membershipService = membershipService;
        this.ticketEventProducer = ticketEventProducer;
        this.jdbcTemplate = jdbcTemplate;
    }

    @KafkaListener(topics = "payment-events", groupId = "ticket-service-group")
    public void handlePaymentEvent(Map<String, Object> event) {
        try {
            // C3: Check explicit type field first, fallback to duck-typing for backward compatibility
            String type = str(event.get("type"));
            if ("PAYMENT_REFUNDED".equals(type)) {
                handleRefund(event);
                return;
            }
            if ("PAYMENT_CONFIRMED".equals(type)) {
                String paymentType = str(event.get("paymentType"));
                switch (paymentType != null ? paymentType : "reservation") {
                    case "transfer" -> handleTransferPayment(event);
                    case "membership" -> handleMembershipPayment(event);
                    default -> handleReservationPayment(event);
                }
                return;
            }

            // Fallback: duck-typing for backward compatibility with events missing type field
            String paymentType = str(event.get("paymentType"));
            boolean isRefund = event.containsKey("reason");

            if (isRefund) {
                handleRefund(event);
                return;
            }

            switch (paymentType != null ? paymentType : "reservation") {
                case "transfer" -> handleTransferPayment(event);
                case "membership" -> handleMembershipPayment(event);
                default -> handleReservationPayment(event);
            }
        } catch (Exception e) {
            log.error("Failed to process payment event: {}", e.getMessage(), e);
        }
    }

    private void handleReservationPayment(Map<String, Object> event) {
        UUID reservationId = uuid(event.get("reservationId"));
        String paymentMethod = str(event.get("paymentMethod"));
        String userId = str(event.get("userId"));
        int amount = integer(event.get("amount"));

        if (reservationId == null) {
            log.warn("Skipping payment event with null reservationId");
            return;
        }

        log.info("Processing reservation payment: reservationId={}", reservationId);
        reservationService.confirmReservationPayment(reservationId, paymentMethod);

        // C4: Look up the reservation to get the eventId instead of passing null
        UUID eventId = lookupEventIdForReservation(reservationId);

        ticketEventProducer.publishReservationConfirmed(new ReservationConfirmedEvent(
            reservationId, userId, eventId, amount, paymentMethod, Instant.now()));
    }

    private void handleTransferPayment(Map<String, Object> event) {
        UUID referenceId = uuid(event.get("referenceId"));
        String userId = str(event.get("userId"));
        String paymentMethod = str(event.get("paymentMethod"));
        int amount = integer(event.get("amount"));

        if (referenceId == null) {
            log.warn("Skipping transfer event with null referenceId");
            return;
        }

        log.info("Processing transfer payment: transferId={}", referenceId);
        transferService.completePurchase(referenceId, userId, paymentMethod);

        // H1: Look up the transfer record to get reservationId and sellerId
        UUID reservationId = null;
        String sellerId = null;
        try {
            List<Map<String, Object>> transferRows = jdbcTemplate.queryForList(
                "SELECT reservation_id, seller_id FROM ticket_transfers WHERE id = ?", referenceId);
            if (!transferRows.isEmpty()) {
                Map<String, Object> transfer = transferRows.getFirst();
                reservationId = (UUID) transfer.get("reservation_id");
                Object sellerIdObj = transfer.get("seller_id");
                sellerId = sellerIdObj != null ? String.valueOf(sellerIdObj) : null;
            }
        } catch (Exception e) {
            log.warn("Failed to look up transfer details for {}: {}", referenceId, e.getMessage());
        }

        ticketEventProducer.publishTransferCompleted(new TransferCompletedEvent(
            referenceId, reservationId, sellerId, userId, amount, Instant.now()));
    }

    private void handleMembershipPayment(Map<String, Object> event) {
        UUID referenceId = uuid(event.get("referenceId"));
        String userId = str(event.get("userId"));

        if (referenceId == null) {
            log.warn("Skipping membership event with null referenceId");
            return;
        }

        log.info("Processing membership payment: membershipId={}", referenceId);
        membershipService.activateMembership(referenceId);

        ticketEventProducer.publishMembershipActivated(new MembershipActivatedEvent(
            referenceId, userId, null, Instant.now()));
    }

    private void handleRefund(Map<String, Object> event) {
        UUID reservationId = uuid(event.get("reservationId"));
        String userId = str(event.get("userId"));
        String reason = str(event.get("reason"));

        if (reservationId == null) {
            log.warn("Skipping refund event with null reservationId");
            return;
        }

        log.info("Processing refund: reservationId={}", reservationId);
        reservationService.markReservationRefunded(reservationId);

        // Look up eventId for the cancelled reservation
        UUID eventId = lookupEventIdForReservation(reservationId);

        ticketEventProducer.publishReservationCancelled(new ReservationCancelledEvent(
            reservationId, userId, eventId, reason, Instant.now()));
    }

    /**
     * Looks up the event_id for a given reservation from the database.
     */
    private UUID lookupEventIdForReservation(UUID reservationId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT event_id FROM reservations WHERE id = ?", reservationId);
            if (!rows.isEmpty() && rows.getFirst().get("event_id") != null) {
                return (UUID) rows.getFirst().get("event_id");
            }
        } catch (Exception e) {
            log.warn("Failed to look up eventId for reservation {}: {}", reservationId, e.getMessage());
        }
        return null;
    }

    private String str(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof String s && !s.isBlank()) {
            try { return UUID.fromString(s); } catch (Exception e) { return null; }
        }
        return null;
    }

    private int integer(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
        }
        return 0;
    }
}
