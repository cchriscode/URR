package guru.urr.catalogservice.domain.admin.service;

import guru.urr.catalogservice.shared.client.TicketInternalClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSeatService {

    private final JdbcTemplate jdbcTemplate;
    private final TicketInternalClient ticketInternalClient;

    public AdminSeatService(JdbcTemplate jdbcTemplate, TicketInternalClient ticketInternalClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
    }

    public Map<String, Object> seatLayouts() {
        return ticketInternalClient.getSeatLayouts();
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
}
