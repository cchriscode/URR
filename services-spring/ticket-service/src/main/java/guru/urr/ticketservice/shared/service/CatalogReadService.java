package guru.urr.ticketservice.shared.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogReadService {

    private final JdbcTemplate jdbcTemplate;

    public CatalogReadService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getTicketsByEvent(UUID eventId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, name, price, total_quantity, available_quantity, description
            FROM ticket_types
            WHERE event_id = ?
            ORDER BY price DESC
            """, eventId);
        return Map.of("ticketTypes", rows);
    }

    public Map<String, Object> getTicketAvailability(UUID ticketTypeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT available_quantity, total_quantity FROM ticket_types WHERE id = ?", ticketTypeId);
        if (rows.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Ticket type not found");
        }
        return rows.getFirst();
    }

    public Map<String, Object> getSeatLayouts() {
        List<Map<String, Object>> layouts = jdbcTemplate.queryForList("""
            SELECT id, name, description, total_seats, layout_config
            FROM seat_layouts
            ORDER BY total_seats ASC
            """);
        return Map.of("layouts", layouts);
    }

    public Map<String, Object> getSeatsByEvent(UUID eventId) {
        List<Map<String, Object>> eventRows = jdbcTemplate.queryForList("""
            SELECT e.id, e.title, e.seat_layout_id, sl.layout_config
            FROM events e
            LEFT JOIN seat_layouts sl ON e.seat_layout_id = sl.id
            WHERE e.id = ?
            """, eventId);

        if (eventRows.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Event not found");
        }

        Map<String, Object> event = eventRows.getFirst();
        if (event.get("seat_layout_id") == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "This event does not have seat selection");
        }

        List<Map<String, Object>> seats = jdbcTemplate.queryForList("""
            SELECT id, section, row_number, seat_number, seat_label, price, status
            FROM seats
            WHERE event_id = ?
            ORDER BY section, row_number, seat_number
            """, eventId);

        Map<String, Object> eventSummary = new HashMap<>();
        eventSummary.put("id", event.get("id"));
        eventSummary.put("title", event.get("title"));

        Map<String, Object> response = new HashMap<>();
        response.put("event", eventSummary);
        response.put("layout", event.get("layout_config"));
        response.put("seats", seats);
        return response;
    }
}
