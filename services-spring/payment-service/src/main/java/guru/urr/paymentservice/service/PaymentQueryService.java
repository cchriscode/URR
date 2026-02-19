package guru.urr.paymentservice.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentQueryService {

    private final JdbcTemplate jdbcTemplate;

    public PaymentQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> findByOrder(String userId, String orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, order_id, payment_key, amount, method, status, toss_status, toss_approved_at, created_at, updated_at, reservation_id, user_id, payment_type, reference_id
            FROM payments
            WHERE order_id = ?
            """, orderId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }

        Map<String, Object> payment = rows.getFirst();
        if (!String.valueOf(payment.get("user_id")).equals(userId) && payment.get("user_id") != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return Map.of("payment", payment);
    }

    public Map<String, Object> myPayments(String userId, int limit, int offset) {
        List<Map<String, Object>> payments = jdbcTemplate.queryForList("""
            SELECT id, order_id, payment_key, amount, method, status, toss_approved_at, created_at, reservation_id, payment_type, reference_id
            FROM payments
            WHERE user_id = CAST(? AS UUID)
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """, userId, limit, offset);

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments WHERE user_id = CAST(? AS UUID)", Integer.class, userId);
        int safeTotal = total != null ? total : 0;

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("total", safeTotal);
        pagination.put("limit", limit);
        pagination.put("offset", offset);

        return Map.of("payments", payments, "pagination", pagination);
    }
}
