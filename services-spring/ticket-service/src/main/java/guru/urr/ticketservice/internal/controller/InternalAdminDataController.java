package guru.urr.ticketservice.internal.controller;

import guru.urr.ticketservice.shared.security.InternalTokenValidator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin")
public class InternalAdminDataController {

    private final JdbcTemplate jdbcTemplate;
    private final InternalTokenValidator internalTokenValidator;

    public InternalAdminDataController(JdbcTemplate jdbcTemplate, InternalTokenValidator internalTokenValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.internalTokenValidator = internalTokenValidator;
    }

    @GetMapping("/reservation-stats")
    public Map<String, Object> reservationStats(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        int totalReservations = intVal(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reservations WHERE status <> 'cancelled'", Integer.class));
        int totalRevenue = intVal(jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(total_amount), 0) FROM reservations WHERE payment_status = 'completed'", Integer.class));
        int todayReservations = intVal(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reservations WHERE DATE(created_at) = CURRENT_DATE AND status <> 'cancelled'", Integer.class));
        return Map.of("totalReservations", totalReservations, "totalRevenue", totalRevenue, "todayReservations", todayReservations);
    }

    @GetMapping("/recent-reservations")
    public Map<String, Object> recentReservations(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        List<Map<String, Object>> recent = jdbcTemplate.queryForList("""
            SELECT r.id, r.user_id, r.reservation_number, r.total_amount, r.status, r.created_at,
                   e.title AS event_title
            FROM reservations r
            LEFT JOIN events e ON r.event_id = e.id
            ORDER BY r.created_at DESC LIMIT 10
            """);
        return Map.of("reservations", recent);
    }

    @GetMapping("/reservations")
    public Map<String, Object> listReservations(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(Math.max(1, limit), 100);
        int offset = (safePage - 1) * safeLimit;

        StringBuilder query = new StringBuilder("""
            SELECT r.id, r.user_id, r.reservation_number, r.total_amount, r.status, r.payment_status,
                   r.created_at, e.title AS event_title, e.venue, e.event_date
            FROM reservations r LEFT JOIN events e ON r.event_id = e.id
            """);
        List<Map<String, Object>> reservations;
        int total;

        if (status != null && !status.isBlank()) {
            query.append(" WHERE r.status = ? ORDER BY r.created_at DESC LIMIT ? OFFSET ?");
            reservations = jdbcTemplate.queryForList(query.toString(), status, safeLimit, offset);
            total = intVal(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations WHERE status = ?", Integer.class, status));
        } else {
            query.append(" ORDER BY r.created_at DESC LIMIT ? OFFSET ?");
            reservations = jdbcTemplate.queryForList(query.toString(), safeLimit, offset);
            total = intVal(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", safePage);
        pagination.put("limit", safeLimit);
        pagination.put("total", total);
        pagination.put("totalPages", (int) Math.ceil((double) total / safeLimit));

        return Map.of("reservations", reservations, "pagination", pagination);
    }

    @PatchMapping("/reservations/{id}/status")
    public Map<String, Object> updateReservationStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        String status = request.get("status");
        String paymentStatus = request.get("paymentStatus");

        if ((status == null || status.isBlank()) && (paymentStatus == null || paymentStatus.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No status fields to update");
        }

        StringBuilder sql = new StringBuilder("UPDATE reservations SET ");
        Map<String, Object> params = new HashMap<>();
        int setCount = 0;
        if (status != null && !status.isBlank()) {
            sql.append("status = :status");
            params.put("status", status);
            setCount++;
        }
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            if (setCount > 0) sql.append(", ");
            sql.append("payment_status = :paymentStatus");
            params.put("paymentStatus", paymentStatus);
        }
        sql.append(", updated_at = NOW() WHERE id = :id RETURNING *");
        params.put("id", id);

        var named = new NamedParameterJdbcTemplate(jdbcTemplate);
        List<Map<String, Object>> rows = named.queryForList(sql.toString(), params);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        return Map.of("reservation", rows.getFirst());
    }

    @PostMapping("/reservations/cancel-by-event/{eventId}")
    public Map<String, Object> cancelReservationsByEvent(
            @PathVariable UUID eventId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        List<Map<String, Object>> cancelled = jdbcTemplate.queryForList("""
            UPDATE reservations SET status = 'cancelled',
                payment_status = CASE WHEN payment_status = 'completed' THEN 'refunded' ELSE payment_status END,
                updated_at = NOW()
            WHERE event_id = ? AND status IN ('pending', 'confirmed') RETURNING id
            """, eventId);

        jdbcTemplate.update(
            "UPDATE seats SET status = 'available', updated_at = NOW() WHERE event_id = ? AND status = 'locked'",
            eventId);

        return Map.of("cancelledCount", cancelled.size());
    }

    @PostMapping("/reservations/cancel-all-by-event/{eventId}")
    public Map<String, Object> cancelAllReservationsByEvent(
            @PathVariable UUID eventId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        List<Map<String, Object>> cancelled = jdbcTemplate.queryForList("""
            UPDATE reservations SET status = 'cancelled',
                payment_status = CASE WHEN payment_status = 'completed' THEN 'refunded' ELSE payment_status END,
                updated_at = NOW()
            WHERE event_id = ? AND status <> 'cancelled' RETURNING id
            """, eventId);
        return Map.of("cancelledCount", cancelled.size());
    }

    @GetMapping("/seat-layouts")
    public Map<String, Object> seatLayouts(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        List<Map<String, Object>> layouts = jdbcTemplate.queryForList(
            "SELECT id, name, description, total_seats, layout_config FROM seat_layouts ORDER BY name");
        return Map.of("layouts", layouts);
    }

    @GetMapping("/active-seat-reservation-count")
    public Map<String, Object> activeSeatReservationCount(
            @RequestParam UUID eventId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        int count = intVal(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM reservations r
            JOIN reservation_items ri ON r.id = ri.reservation_id
            WHERE r.event_id = ? AND ri.seat_id IS NOT NULL AND r.status <> 'cancelled'
            """, Integer.class, eventId));
        return Map.of("count", count);
    }

    private int intVal(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }
}
