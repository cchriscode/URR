package guru.urr.paymentservice.service;

import guru.urr.paymentservice.client.TicketInternalClient;
import guru.urr.paymentservice.dto.CancelPaymentRequest;
import guru.urr.paymentservice.dto.ConfirmPaymentRequest;
import guru.urr.paymentservice.dto.PreparePaymentRequest;
import guru.urr.paymentservice.dto.ProcessPaymentRequest;
import guru.urr.paymentservice.messaging.PaymentEventProducer;
import guru.urr.paymentservice.messaging.event.PaymentRefundedEvent;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TicketInternalClient ticketInternalClient;
    private final PaymentEventProducer paymentEventProducer;
    private final PaymentTypeDispatcher paymentTypeDispatcher;
    private final String tossClientKey;

    public PaymentService(
        JdbcTemplate jdbcTemplate,
        TicketInternalClient ticketInternalClient,
        PaymentEventProducer paymentEventProducer,
        PaymentTypeDispatcher paymentTypeDispatcher,
        @Value("${TOSS_CLIENT_KEY:test_ck_dummy}") String tossClientKey
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
        this.paymentEventProducer = paymentEventProducer;
        this.paymentTypeDispatcher = paymentTypeDispatcher;
        this.tossClientKey = tossClientKey;
    }

    @Transactional
    public Map<String, Object> prepare(String userId, PreparePaymentRequest request) {
        String paymentType = request.paymentType() != null ? request.paymentType() : "reservation";

        int validatedAmount;
        String eventIdText = null;
        String reservationIdText = null;
        UUID referenceId = request.referenceId();

        switch (paymentType) {
            case "transfer" -> {
                if (referenceId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceId required for transfer");
                Map<String, Object> transfer = ticketInternalClient.validateTransfer(referenceId, userId);
                validatedAmount = requiredInt(transfer, "total_amount", "totalAmount");
            }
            case "membership" -> {
                if (referenceId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceId required for membership");
                Map<String, Object> membership = ticketInternalClient.validateMembership(referenceId, userId);
                validatedAmount = requiredInt(membership, "total_amount", "totalAmount");
            }
            default -> {
                // reservation (existing flow)
                if (request.reservationId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reservationId required");
                Map<String, Object> reservation = ticketInternalClient.validateReservation(request.reservationId(), userId);
                validatedAmount = requiredInt(reservation, "total_amount", "totalAmount");
                Object eventId = firstPresent(reservation, "event_id", "eventId");
                eventIdText = String.valueOf(eventId);
                reservationIdText = request.reservationId().toString();
            }
        }

        if (validatedAmount != request.amount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount mismatch");
        }

        // Check for existing payment
        String lookupSql;
        Object[] lookupParams;
        if ("reservation".equals(paymentType) && request.reservationId() != null) {
            lookupSql = "SELECT id, order_id, status FROM payments WHERE reservation_id = ?";
            lookupParams = new Object[]{request.reservationId()};
        } else if (referenceId != null) {
            lookupSql = "SELECT id, order_id, status FROM payments WHERE reference_id = ? AND payment_type = ?";
            lookupParams = new Object[]{referenceId, paymentType};
        } else {
            lookupSql = null;
            lookupParams = null;
        }

        if (lookupSql != null) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(lookupSql, lookupParams);
            if (!existing.isEmpty()) {
                Map<String, Object> payment = existing.getFirst();
                if ("confirmed".equals(String.valueOf(payment.get("status")))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment already confirmed");
                }
                return Map.of("orderId", payment.get("order_id"), "amount", request.amount(), "clientKey", tossClientKey);
            }
        }

        String orderId = "ORD_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        jdbcTemplate.update("""
            INSERT INTO payments (reservation_id, user_id, event_id, order_id, amount, status, payment_type, reference_id)
            VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), ?, ?, 'pending', ?, CAST(? AS UUID))
            """,
            reservationIdText, userId, eventIdText, orderId, request.amount(),
            paymentType, referenceId != null ? referenceId.toString() : null);

        return Map.of("orderId", orderId, "amount", request.amount(), "clientKey", tossClientKey);
    }

    @Transactional
    public Map<String, Object> confirm(String userId, ConfirmPaymentRequest request) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, reservation_id, user_id, amount, status, payment_type, reference_id
            FROM payments
            WHERE order_id = ?
            FOR UPDATE
            """, request.orderId());

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }

        Map<String, Object> payment = rows.getFirst();
        if (!String.valueOf(payment.get("user_id")).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (((Number) payment.get("amount")).intValue() != request.amount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount mismatch");
        }
        if ("confirmed".equals(String.valueOf(payment.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment already confirmed");
        }

        String paymentType = String.valueOf(payment.get("payment_type"));

        // Validate based on type
        if ("reservation".equals(paymentType)) {
            UUID reservationId = asUuid(payment.get("reservation_id"), "reservation_id");
            ticketInternalClient.validateReservation(reservationId, userId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        jdbcTemplate.update("""
            UPDATE payments
            SET payment_key = ?, method = 'toss', status = 'confirmed',
                toss_status = 'DONE', toss_approved_at = ?, updated_at = NOW()
            WHERE id = ?
            """, request.paymentKey(), now, payment.get("id"));

        // Complete based on type
        paymentTypeDispatcher.completeByType(paymentType, payment, userId, "toss");

        return Map.of(
            "success", true,
            "payment", Map.of(
                "orderId", request.orderId(),
                "paymentKey", request.paymentKey(),
                "amount", request.amount(),
                "method", "toss",
                "approvedAt", now.toString()
            )
        );
    }

    @Transactional
    public Map<String, Object> cancel(String userId, String paymentKey, CancelPaymentRequest request) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, reservation_id, user_id, amount, status
            FROM payments
            WHERE payment_key = ?
            FOR UPDATE
            """, paymentKey);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }

        Map<String, Object> payment = rows.getFirst();
        if (!String.valueOf(payment.get("user_id")).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (!"confirmed".equals(String.valueOf(payment.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only confirmed payments can be cancelled");
        }

        String reason = request != null && request.cancelReason() != null ? request.cancelReason() : "user-request";
        jdbcTemplate.update("""
            UPDATE payments
            SET status = 'refunded', refund_amount = amount, refund_reason = ?, refunded_at = NOW(), updated_at = NOW()
            WHERE id = ?
            """, reason, payment.get("id"));

        UUID reservationId = asUuidNullable(payment.get("reservation_id"));
        paymentEventProducer.publishRefund(new PaymentRefundedEvent(
            (UUID) payment.get("id"), null, userId, reservationId, null,
            "reservation", ((Number) payment.get("amount")).intValue(), reason, Instant.now()));

        return Map.of("success", true, "message", "Payment cancelled successfully", "refundAmount", payment.get("amount"));
    }

    @Transactional
    public Map<String, Object> process(String userId, ProcessPaymentRequest request) {
        String paymentType = request.paymentType() != null ? request.paymentType() : "reservation";
        UUID referenceId = request.referenceId();

        int totalAmount;
        String eventIdText = null;
        String reservationIdText = null;

        switch (paymentType) {
            case "transfer" -> {
                if (referenceId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceId required");
                Map<String, Object> transfer = ticketInternalClient.validateTransfer(referenceId, userId);
                totalAmount = requiredInt(transfer, "total_amount", "totalAmount");
            }
            case "membership" -> {
                if (referenceId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "referenceId required");
                Map<String, Object> membership = ticketInternalClient.validateMembership(referenceId, userId);
                totalAmount = requiredInt(membership, "total_amount", "totalAmount");
            }
            default -> {
                if (request.reservationId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reservationId required");
                Map<String, Object> reservation = ticketInternalClient.validateReservation(request.reservationId(), userId);
                Object eventId = firstPresent(reservation, "event_id", "eventId");
                totalAmount = requiredInt(reservation, "total_amount", "totalAmount");
                eventIdText = String.valueOf(eventId);
                reservationIdText = request.reservationId().toString();
            }
        }

        // Idempotency: check for existing confirmed payment
        String lookupSql;
        Object[] lookupParams;
        if ("reservation".equals(paymentType) && request.reservationId() != null) {
            lookupSql = "SELECT id, order_id, amount, status FROM payments WHERE reservation_id = ? FOR UPDATE";
            lookupParams = new Object[]{request.reservationId()};
        } else if (referenceId != null) {
            lookupSql = "SELECT id, order_id, amount, status FROM payments WHERE reference_id = ? AND payment_type = ? FOR UPDATE";
            lookupParams = new Object[]{referenceId, paymentType};
        } else {
            lookupSql = null;
            lookupParams = null;
        }

        if (lookupSql != null) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(lookupSql, lookupParams);
            if (!existing.isEmpty()) {
                Map<String, Object> payment = existing.getFirst();
                if ("confirmed".equals(String.valueOf(payment.get("status")))) {
                    return Map.of(
                        "success", true,
                        "message", "Payment already completed",
                        "payment", Map.of(
                            "id", payment.get("id"),
                            "orderId", payment.get("order_id"),
                            "amount", payment.get("amount"),
                            "method", request.paymentMethod()
                        )
                    );
                }
            }
        }

        String orderId = "ORD_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        UUID paymentId = jdbcTemplate.queryForObject("""
            INSERT INTO payments (user_id, event_id, reservation_id, order_id, amount, method, status, payment_type, reference_id)
            VALUES (CAST(? AS UUID), CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, 'confirmed', ?, CAST(? AS UUID))
            RETURNING id
            """, UUID.class,
            userId, eventIdText, reservationIdText, orderId, totalAmount, request.paymentMethod(),
            paymentType, referenceId != null ? referenceId.toString() : null);

        // Complete based on type
        Map<String, Object> paymentMap = Map.of(
            "reservation_id", reservationIdText != null ? reservationIdText : "",
            "reference_id", referenceId != null ? referenceId.toString() : "",
            "payment_type", paymentType
        );
        paymentTypeDispatcher.completeByType(paymentType, paymentMap, userId, request.paymentMethod());

        return Map.of(
            "success", true,
            "message", "Payment completed",
            "payment", Map.of(
                "id", paymentId,
                "orderId", orderId,
                "amount", totalAmount,
                "method", request.paymentMethod()
            )
        );
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return map.get(key);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Required field missing from ticket response");
    }

    private int requiredInt(Map<String, Object> map, String... keys) {
        Object value = firstPresent(map, keys);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid numeric field from ticket response");
        }
    }

    private UUID asUuid(Object value, String fieldName) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid UUID field: " + fieldName);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Missing UUID field: " + fieldName);
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
