package com.tiketi.catalogservice.domain.admin.service;

import com.tiketi.catalogservice.shared.client.AuthInternalClient;
import com.tiketi.catalogservice.shared.client.TicketInternalClient;
import com.tiketi.catalogservice.domain.admin.dto.AdminEventRequest;
import com.tiketi.catalogservice.domain.admin.dto.AdminReservationStatusRequest;
import com.tiketi.catalogservice.domain.admin.dto.AdminTicketTypeRequest;
import com.tiketi.catalogservice.domain.admin.dto.AdminTicketUpdateRequest;
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
    private final TicketInternalClient ticketInternalClient;
    private final AuthInternalClient authInternalClient;

    public AdminService(
        JdbcTemplate jdbcTemplate,
        TicketInternalClient ticketInternalClient,
        AuthInternalClient authInternalClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
        this.authInternalClient = authInternalClient;
    }

    public Map<String, Object> dashboardStats() {
        int totalEvents = intValue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class));

        Map<String, Object> reservationStats = ticketInternalClient.getReservationStats();
        int totalReservations = intValue(reservationStats.get("totalReservations"));
        int totalRevenue = intValue(reservationStats.get("totalRevenue"));
        int todayReservations = intValue(reservationStats.get("todayReservations"));

        List<Map<String, Object>> recent = ticketInternalClient.getRecentReservations();

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
        return ticketInternalClient.getSeatLayouts();
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
            ticketInternalClient.generateSeats(eventId, request.seatLayoutId());
        }

        if (request.ticketTypes() != null && !request.ticketTypes().isEmpty()) {
            for (AdminTicketTypeRequest ticketType : request.ticketTypes()) {
                ticketInternalClient.createTicketType(eventId, ticketType.name(), ticketType.price(), ticketType.totalQuantity(), ticketType.description());
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

        int cancelledCount = ticketInternalClient.cancelReservationsByEvent(eventId);

        return Map.of(
            "message", "Event cancelled successfully. Reservations were cancelled and refundable cases marked refunded.",
            "event", eventRows.getFirst(),
            "cancelledReservations", cancelledCount
        );
    }

    @Transactional
    public Map<String, Object> deleteEvent(UUID eventId) {
        int cancelledCount = ticketInternalClient.cancelAllReservationsByEvent(eventId);

        List<Map<String, Object>> deletedEvent = jdbcTemplate.queryForList(
            "DELETE FROM events WHERE id = ? RETURNING id, title", eventId);
        if (deletedEvent.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }

        return Map.of("message", "Event deleted successfully", "cancelledReservations", cancelledCount);
    }

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
        int existing = ticketInternalClient.countSeats(eventId);
        if (existing > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seats already generated for this event");
        }

        int created = ticketInternalClient.generateSeats(eventId, (UUID) event.get("seat_layout_id"));
        return Map.of("message", "Seats generated successfully", "seatsCreated", created, "eventTitle", event.get("title"));
    }

    public Map<String, Object> deleteSeats(UUID eventId) {
        int activeReservations = ticketInternalClient.getActiveSeatReservationCount(eventId);
        if (activeReservations > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete seats with active reservations");
        }

        int deleted = ticketInternalClient.deleteSeats(eventId);
        return Map.of("message", "Seats deleted successfully", "seatsDeleted", deleted);
    }

    public Map<String, Object> createTicketType(UUID eventId, AdminTicketTypeRequest request) {
        Map<String, Object> result = ticketInternalClient.createTicketType(
            eventId, request.name(), request.price(), request.totalQuantity(), request.description());
        return Map.of("message", "Ticket type created", "ticketType", result.get("ticketType"));
    }

    public Map<String, Object> updateTicketType(UUID ticketTypeId, AdminTicketUpdateRequest request) {
        Map<String, Object> result = ticketInternalClient.updateTicketType(
            ticketTypeId, request.name(), request.price(), request.totalQuantity(), request.description());
        return Map.of("message", "Ticket type updated", "ticketType", result.get("ticketType"));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listReservations(Integer page, Integer limit, String status) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);

        Map<String, Object> result = ticketInternalClient.listReservations(safePage, safeLimit, status);

        List<Map<String, Object>> reservations = result.get("reservations") instanceof List<?> list
            ? (List<Map<String, Object>>) list
            : List.of();

        hydrateUserInfo(reservations);

        Map<String, Object> response = new HashMap<>();
        response.put("reservations", reservations);
        response.put("pagination", result.get("pagination"));
        return response;
    }

    public Map<String, Object> updateReservationStatus(UUID reservationId, AdminReservationStatusRequest request) {
        if ((request.status() == null || request.status().isBlank())
            && (request.paymentStatus() == null || request.paymentStatus().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No status fields to update");
        }

        Map<String, Object> result = ticketInternalClient.updateReservationStatus(
            reservationId, request.status(), request.paymentStatus());
        return Map.of("message", "Reservation status updated", "reservation", result.get("reservation"));
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
