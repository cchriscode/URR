package guru.urr.ticketservice.internal.controller;

import guru.urr.common.security.InternalTokenValidator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/ticket-types")
public class InternalTicketTypeController {

    private final JdbcTemplate jdbcTemplate;
    private final InternalTokenValidator internalTokenValidator;

    public InternalTicketTypeController(JdbcTemplate jdbcTemplate, InternalTokenValidator internalTokenValidator) {
        this.jdbcTemplate = jdbcTemplate;
        this.internalTokenValidator = internalTokenValidator;
    }

    @GetMapping
    public Map<String, Object> listByEvent(
            @RequestParam UUID eventId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, name, price, total_quantity, available_quantity, description FROM ticket_types WHERE event_id = ? ORDER BY price DESC",
            eventId);
        return Map.of("ticketTypes", rows);
    }

    @GetMapping("/{id}/availability")
    public Map<String, Object> getAvailability(
            @PathVariable UUID id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT available_quantity, total_quantity FROM ticket_types WHERE id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found");
        return rows.getFirst();
    }

    @PostMapping
    public Map<String, Object> create(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        UUID eventId = UUID.fromString((String) request.get("eventId"));
        String name = (String) request.get("name");
        int price = ((Number) request.get("price")).intValue();
        int totalQuantity = ((Number) request.get("totalQuantity")).intValue();
        String description = (String) request.get("description");

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
            "INSERT INTO ticket_types (event_id, name, price, total_quantity, available_quantity, description) VALUES (?, ?, ?, ?, ?, ?) RETURNING *",
            eventId, name, price, totalQuantity, totalQuantity, description);
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create");
        return Map.of("ticketType", result.getFirst());
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        List<Map<String, Object>> currentRows = jdbcTemplate.queryForList(
            "SELECT total_quantity, available_quantity FROM ticket_types WHERE id = ?", id);
        if (currentRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found");

        Map<String, Object> current = currentRows.getFirst();
        int oldTotal = ((Number) current.get("total_quantity")).intValue();
        int oldAvailable = ((Number) current.get("available_quantity")).intValue();
        int sold = oldTotal - oldAvailable;

        String name = (String) request.get("name");
        int price = ((Number) request.get("price")).intValue();
        int totalQuantity = ((Number) request.get("totalQuantity")).intValue();
        String description = (String) request.get("description");

        int newAvailable = totalQuantity - sold;
        if (newAvailable < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot set quantity below sold amount");

        List<Map<String, Object>> result = jdbcTemplate.queryForList(
            "UPDATE ticket_types SET name = ?, price = ?, total_quantity = ?, available_quantity = ?, description = ?, updated_at = NOW() WHERE id = ? RETURNING *",
            name, price, totalQuantity, newAvailable, description, id);
        if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to update");
        return Map.of("ticketType", result.getFirst());
    }
}
