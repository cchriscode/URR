package guru.urr.paymentservice.controller;

import guru.urr.paymentservice.security.InternalTokenValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final InternalTokenValidator internalTokenValidator;
    private final JdbcTemplate jdbcTemplate;

    public InternalPaymentController(InternalTokenValidator internalTokenValidator, JdbcTemplate jdbcTemplate) {
        this.internalTokenValidator = internalTokenValidator;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Internal endpoint for ticket-service reconciliation.
     * Returns the payment status for a given reservation.
     */
    @GetMapping("/by-reservation/{reservationId}")
    public Map<String, Object> getPaymentByReservation(
        @PathVariable UUID reservationId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        internalTokenValidator.requireValidToken(authorization);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, order_id, status, method, amount, payment_type, created_at
            FROM payments
            WHERE reservation_id = ?
            ORDER BY created_at DESC
            LIMIT 1
            """, reservationId);

        if (rows.isEmpty()) {
            return Map.of("found", false, "reservationId", reservationId.toString());
        }

        Map<String, Object> payment = rows.getFirst();
        return Map.of(
            "found", true,
            "reservationId", reservationId.toString(),
            "status", String.valueOf(payment.get("status")),
            "method", payment.get("method") != null ? String.valueOf(payment.get("method")) : "",
            "amount", payment.get("amount")
        );
    }
}
