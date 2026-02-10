package com.tiketi.ticketservice.service;

import com.tiketi.ticketservice.client.AuthInternalClient;
import com.tiketi.ticketservice.dto.AdminEventRequest;
import com.tiketi.ticketservice.dto.AdminReservationStatusRequest;
import com.tiketi.ticketservice.dto.AdminTicketTypeRequest;
import com.tiketi.ticketservice.dto.AdminTicketUpdateRequest;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private final JdbcTemplate jdbcTemplate;
    private final SeatGeneratorService seatGeneratorService;
    private final AuthInternalClient authInternalClient;

    public AdminService(
        JdbcTemplate jdbcTemplate,
        SeatGeneratorService seatGeneratorService,
        AuthInternalClient authInternalClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.seatGeneratorService = seatGeneratorService;
        this.authInternalClient = authInternalClient;
    }

    public Map<String, Object> dashboardStats() {
        int totalEvents = intValue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class));
        int totalReservations = intValue(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reservations WHERE status <> 'cancelled'", Integer.class));
        int totalRevenue = intValue(jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(total_amount), 0) FROM reservations WHERE payment_status = 'completed'", Integer.class));
        int todayReservations = intValue(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM reservations WHERE DATE(created_at) = CURRENT_DATE AND status <> 'cancelled'", Integer.class));

        List<Map<String, Object>> recent = jdbcTemplate.queryForList("""
            SELECT
              r.id, r.user_id, r.reservation_number, r.total_amount, r.status, r.created_at,
              e.title AS event_title
            FROM reservations r
            LEFT JOIN events e ON r.event_id = e.id
            ORDER BY r.created_at DESC
            LIMIT 10
            """);

        hydrateUserInfo(recent);

        return Map.of(
            "stats", Map.of(
                "totalEvents", totalEvents,
                "totalReservations", totalReservations,
                "totalRevenue", totalRevenue,
                "todayReservations", todayReservations
            ),
            "recentReservations", recent
        );
    }

    public Map<String, Object> seatLayouts() {
        List<Map<String, Object>> layouts = jdbcTemplate.queryForList(
            "SELECT id, name, description, total_seats, layout_config FROM seat_layouts ORDER BY name");
        return Map.of("layouts", layouts);
    }

    @Transactional
    public Map<String, Object> createEvent(AdminEventRequest request, String adminUserId) {
        if (request.saleEndDate().isBefore(request.saleStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "saleEndDate must be later than saleStartDate");
        }

        String status = calculateEventStatus(request.saleStartDate(), request.saleEndDate(), "upcoming");

        UUID eventId = jdbcTemplate.queryForObject("""
            INSERT INTO events
            (title, description, venue, address, event_date, sale_start_date, sale_end_date, poster_image_url, artist_name, seat_layout_id, created_by, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS UUID), ?)
            RETURNING id
            """, UUID.class,
            request.title(),
            request.description(),
            request.venue(),
            request.address(),
            Timestamp.from(request.eventDate().toInstant()),
            Timestamp.from(request.saleStartDate().toInstant()),
            Timestamp.from(request.saleEndDate().toInstant()),
            request.posterImageUrl(),
            request.artistName(),
            request.seatLayoutId(),
            adminUserId,
            status
        );

        if (request.seatLayoutId() != null) {
            seatGeneratorService.generateSeatsForEvent(eventId, request.seatLayoutId());
        }

        if (request.ticketTypes() != null && !request.ticketTypes().isEmpty()) {
            for (AdminTicketTypeRequest ticketType : request.ticketTypes()) {
                jdbcTemplate.update("""
                    INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, eventId, ticketType.name(), ticketType.price(), ticketType.totalQuantity(), ticketType.totalQuantity(), ticketType.description());
            }
        }

        Map<String, Object> event = jdbcTemplate.queryForList("SELECT * FROM events WHERE id = ?", eventId).getFirst();
        return Map.of("message", "Event created successfully", "event", event);
    }

    @Transactional
    public Map<String, Object> updateEvent(UUID eventId, AdminEventRequest request) {
        int updated = jdbcTemplate.update("""
            UPDATE events
            SET title = ?, description = ?, venue = ?, address = ?,
                event_date = ?, sale_start_date = ?, sale_end_date = ?,
                poster_image_url = ?, artist_name = ?, updated_at = NOW()
            WHERE id = ?
            """,
            request.title(),
            request.description(),
            request.venue(),
            request.address(),
            Timestamp.from(request.eventDate().toInstant()),
            Timestamp.from(request.saleStartDate().toInstant()),
            Timestamp.from(request.saleEndDate().toInstant()),
            request.posterImageUrl(),
            request.artistName(),
            eventId);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }

        Map<String, Object> event = jdbcTemplate.queryForList("SELECT * FROM events WHERE id = ?", eventId).getFirst();
        String currentStatus = String.valueOf(event.get("status"));
        if (!"cancelled".equalsIgnoreCase(currentStatus)) {
            String newStatus = calculateEventStatus(toOffsetDateTime(event.get("sale_start_date")), toOffsetDateTime(event.get("sale_end_date")), currentStatus);
            if (!Objects.equals(newStatus, currentStatus)) {
                jdbcTemplate.update("UPDATE events SET status = ?, updated_at = NOW() WHERE id = ?", newStatus, eventId);
                event.put("status", newStatus);
            }
        }

        return Map.of("message", "Event updated successfully", "event", event);
    }

    @Transactional
    public Map<String, Object> cancelEvent(UUID eventId) {
        List<Map<String, Object>> eventRows = jdbcTemplate.queryForList("""
            UPDATE events
            SET status = 'cancelled', updated_at = NOW()
            WHERE id = ? AND status <> 'cancelled'
            RETURNING *
            """, eventId);
        if (eventRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found or already cancelled");
        }

        List<Map<String, Object>> cancelledReservations = jdbcTemplate.queryForList("""
            UPDATE reservations
            SET status = 'cancelled',
                payment_status = CASE WHEN payment_status = 'completed' THEN 'refunded' ELSE payment_status END,
                updated_at = NOW()
            WHERE event_id = ? AND status IN ('pending', 'confirmed')
            RETURNING id
            """, eventId);

        jdbcTemplate.update("""
            UPDATE seats
            SET status = 'available', updated_at = NOW()
            WHERE event_id = ? AND status = 'locked'
            """, eventId);

        return Map.of(
            "message", "Event cancelled successfully. Reservations were cancelled and refundable cases marked refunded.",
            "event", eventRows.getFirst(),
            "cancelledReservations", cancelledReservations.size()
        );
    }

    @Transactional
    public Map<String, Object> deleteEvent(UUID eventId) {
        List<Map<String, Object>> cancelledReservations = jdbcTemplate.queryForList("""
            UPDATE reservations
            SET status = 'cancelled',
                payment_status = CASE WHEN payment_status = 'completed' THEN 'refunded' ELSE payment_status END,
                updated_at = NOW()
            WHERE event_id = ? AND status <> 'cancelled'
            RETURNING id
            """, eventId);

        List<Map<String, Object>> deletedEvent = jdbcTemplate.queryForList(
            "DELETE FROM events WHERE id = ? RETURNING id, title", eventId);
        if (deletedEvent.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }

        return Map.of("message", "Event deleted successfully", "cancelledReservations", cancelledReservations.size());
    }

    @Transactional
    public Map<String, Object> generateSeats(UUID eventId) {
        List<Map<String, Object>> eventRows = jdbcTemplate.queryForList(
            "SELECT id, title, seat_layout_id FROM events WHERE id = ?", eventId);
        if (eventRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        Map<String, Object> event = eventRows.getFirst();
        if (event.get("seat_layout_id") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat layout is not configured for this event");
        }
        int existing = seatGeneratorService.countSeats(eventId);
        if (existing > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seats already generated for this event");
        }

        int created = seatGeneratorService.generateSeatsForEvent(eventId, (UUID) event.get("seat_layout_id"));
        return Map.of("message", "Seats generated successfully", "seatsCreated", created, "eventTitle", event.get("title"));
    }

    @Transactional
    public Map<String, Object> deleteSeats(UUID eventId) {
        int activeReservations = intValue(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM reservations r
            JOIN reservation_items ri ON r.id = ri.reservation_id
            WHERE r.event_id = ? AND ri.seat_id IS NOT NULL AND r.status <> 'cancelled'
            """, Integer.class, eventId));
        if (activeReservations > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete seats with active reservations");
        }

        int deleted = seatGeneratorService.deleteSeatsForEvent(eventId);
        return Map.of("message", "Seats deleted successfully", "seatsDeleted", deleted);
    }

    @Transactional
    public Map<String, Object> createTicketType(UUID eventId, AdminTicketTypeRequest request) {
        Map<String, Object> ticketType = jdbcTemplate.queryForList("""
            INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING *
            """, eventId, request.name(), request.price(), request.totalQuantity(), request.totalQuantity(), request.description())
            .stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create ticket type"));

        return Map.of("message", "Ticket type created", "ticketType", ticketType);
    }

    @Transactional
    public Map<String, Object> updateTicketType(UUID ticketTypeId, AdminTicketUpdateRequest request) {
        List<Map<String, Object>> currentRows = jdbcTemplate.queryForList(
            "SELECT total_quantity, available_quantity, event_id FROM ticket_types WHERE id = ?", ticketTypeId);
        if (currentRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found");
        }

        Map<String, Object> current = currentRows.getFirst();
        int sold = intValue(current.get("total_quantity")) - intValue(current.get("available_quantity"));
        int newAvailable = request.totalQuantity() - sold;
        if (newAvailable < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot set quantity below sold amount");
        }

        Map<String, Object> updated = jdbcTemplate.queryForList("""
            UPDATE ticket_types
            SET name = ?, price = ?, total_quantity = ?, available_quantity = ?, description = ?, updated_at = NOW()
            WHERE id = ?
            RETURNING *
            """, request.name(), request.price(), request.totalQuantity(), newAvailable, request.description(), ticketTypeId)
            .stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to update ticket type"));

        return Map.of("message", "Ticket type updated", "ticketType", updated);
    }

    public Map<String, Object> listReservations(Integer page, Integer limit, String status) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        int offset = (safePage - 1) * safeLimit;

        StringBuilder query = new StringBuilder("""
            SELECT
              r.id, r.user_id, r.reservation_number, r.total_amount, r.status, r.payment_status,
              r.created_at, e.title AS event_title, e.venue, e.event_date
            FROM reservations r
            LEFT JOIN events e ON r.event_id = e.id
            """);

        if (status != null && !status.isBlank()) {
            query.append(" WHERE r.status = ?");
        }
        query.append(" ORDER BY r.created_at DESC LIMIT ? OFFSET ?");

        List<Map<String, Object>> reservations;
        if (status != null && !status.isBlank()) {
            reservations = jdbcTemplate.queryForList(query.toString(), status, safeLimit, offset);
        } else {
            reservations = jdbcTemplate.queryForList(query.toString(), safeLimit, offset);
        }

        hydrateUserInfo(reservations);

        int total;
        if (status != null && !status.isBlank()) {
            total = intValue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations WHERE status = ?", Integer.class, status));
        } else {
            total = intValue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations", Integer.class));
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", safePage);
        pagination.put("limit", safeLimit);
        pagination.put("total", total);
        pagination.put("totalPages", (int) Math.ceil((double) total / safeLimit));

        return Map.of("reservations", reservations, "pagination", pagination);
    }

    @Transactional
    public Map<String, Object> updateReservationStatus(UUID reservationId, AdminReservationStatusRequest request) {
        if ((request.status() == null || request.status().isBlank())
            && (request.paymentStatus() == null || request.paymentStatus().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No status fields to update");
        }

        StringBuilder sql = new StringBuilder("UPDATE reservations SET ");
        Map<String, Object> params = new HashMap<>();
        int setCount = 0;
        if (request.status() != null && !request.status().isBlank()) {
            sql.append("status = :status");
            params.put("status", request.status());
            setCount++;
        }
        if (request.paymentStatus() != null && !request.paymentStatus().isBlank()) {
            if (setCount > 0) {
                sql.append(", ");
            }
            sql.append("payment_status = :paymentStatus");
            params.put("paymentStatus", request.paymentStatus());
        }
        sql.append(", updated_at = NOW() WHERE id = :id RETURNING *");
        params.put("id", reservationId);

        org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate named =
            new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate);

        List<Map<String, Object>> rows = named.queryForList(sql.toString(), params);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }
        return Map.of("message", "Reservation status updated", "reservation", rows.getFirst());
    }

    private void hydrateUserInfo(List<Map<String, Object>> reservations) {
        List<UUID> userIds = reservations.stream()
            .map(r -> r.get("user_id"))
            .filter(Objects::nonNull)
            .map(v -> v instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(v)))
            .distinct()
            .toList();

        Map<UUID, Map<String, Object>> users = authInternalClient.findUsersByIds(userIds);
        for (Map<String, Object> reservation : reservations) {
            Object rawUserId = reservation.get("user_id");
            if (rawUserId == null) {
                reservation.put("user_name", null);
                reservation.put("user_email", null);
                continue;
            }
            UUID userId = rawUserId instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(rawUserId));
            Map<String, Object> user = users.get(userId);
            reservation.put("user_name", user != null ? user.get("name") : null);
            reservation.put("user_email", user != null ? user.get("email") : null);
        }
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String calculateEventStatus(OffsetDateTime saleStartDate, OffsetDateTime saleEndDate, String fallback) {
        if (saleStartDate == null || saleEndDate == null) {
            return fallback;
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(saleStartDate)) {
            return "upcoming";
        }
        if (now.isAfter(saleEndDate)) {
            return "ended";
        }
        return "on_sale";
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(OffsetDateTime.now().getOffset());
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atOffset(OffsetDateTime.now().getOffset());
        }
        return OffsetDateTime.now();
    }
}
