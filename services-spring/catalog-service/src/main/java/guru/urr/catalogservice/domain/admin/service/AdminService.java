package guru.urr.catalogservice.domain.admin.service;

import guru.urr.catalogservice.shared.client.TicketInternalClient;
import guru.urr.catalogservice.domain.admin.dto.AdminEventRequest;
import guru.urr.catalogservice.domain.admin.dto.AdminTicketTypeRequest;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
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

    public AdminService(JdbcTemplate jdbcTemplate, TicketInternalClient ticketInternalClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
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
