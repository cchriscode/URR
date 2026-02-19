package guru.urr.catalogservice.domain.event.service;

import guru.urr.catalogservice.shared.client.TicketInternalClient;
import guru.urr.common.util.PreSaleSchedule;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EventReadService {

    private final JdbcTemplate jdbcTemplate;
    private final TicketInternalClient ticketInternalClient;

    public EventReadService(JdbcTemplate jdbcTemplate, TicketInternalClient ticketInternalClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
    }

    // TODO: listEvents still JOINs ticket_types directly for min/max price aggregation - move to internal API when performance allows
    public Map<String, Object> listEvents(String status, String searchQuery, int page, int limit) {
        int offset = (page - 1) * limit;

        StringBuilder sql = new StringBuilder("""
            SELECT
              e.id, e.title, e.description, e.venue, e.address,
              e.event_date, e.sale_start_date, e.sale_end_date,
              e.poster_image_url, e.status, e.artist_name,
              COUNT(DISTINCT tt.id) as ticket_type_count,
              MIN(tt.price) as min_price,
              MAX(tt.price) as max_price
            FROM events e
            LEFT JOIN ticket_types tt ON e.id = tt.event_id
            """);

        List<Object> params = new ArrayList<>();
        List<String> whereConditions = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            whereConditions.add("e.status = ?");
            params.add(status);
        }

        if (searchQuery != null && !searchQuery.isBlank()) {
            String q = "%" + searchQuery.trim().replace("%", "\\%").replace("_", "\\_") + "%";
            whereConditions.add("(COALESCE(e.title,'') || ' ' || COALESCE(e.artist_name,'') || ' ' || COALESCE(e.venue,'') || ' ' || COALESCE(e.address,'')) ILIKE ?");
            params.add(q);
        }

        if (!whereConditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereConditions));
        }

        sql.append(" GROUP BY e.id ORDER BY e.sale_start_date ASC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        List<Map<String, Object>> events = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM events e");
        List<Object> countParams = new ArrayList<>();
        if (!whereConditions.isEmpty()) {
            List<String> countWhere = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                countWhere.add("e.status = ?");
                countParams.add(status);
            }
            if (searchQuery != null && !searchQuery.isBlank()) {
                countWhere.add("(COALESCE(e.title,'') || ' ' || COALESCE(e.artist_name,'') || ' ' || COALESCE(e.venue,'') || ' ' || COALESCE(e.address,'')) ILIKE ?");
                countParams.add("%" + searchQuery.trim().replace("%", "\\%").replace("_", "\\_") + "%");
            }
            countSql.append(" WHERE ").append(String.join(" AND ", countWhere));
        }

        Integer total = jdbcTemplate.queryForObject(countSql.toString(), Integer.class, countParams.toArray());
        int safeTotal = total != null ? total : 0;

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("total", safeTotal);
        pagination.put("totalPages", (int) Math.ceil((double) safeTotal / limit));

        Map<String, Object> response = new HashMap<>();
        response.put("events", events);
        response.put("pagination", pagination);
        return response;
    }

    public Map<String, Object> getEventDetail(UUID eventId) {
        List<Map<String, Object>> eventRows = jdbcTemplate.queryForList("SELECT * FROM events WHERE id = ?", eventId);
        if (eventRows.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Event not found");
        }

        List<Map<String, Object>> ticketTypes = ticketInternalClient.getTicketTypesByEvent(eventId);

        Map<String, Object> event = eventRows.getFirst();
        Map<String, Object> result = new HashMap<>();
        result.put("event", event);
        result.put("ticketTypes", ticketTypes);

        Object artistId = event.get("artist_id");
        if (artistId != null) {
            Object saleStart = event.get("sale_start_date");
            if (saleStart instanceof OffsetDateTime odt) {
                result.put("preSaleSchedule", PreSaleSchedule.compute(odt));
            } else if (saleStart instanceof java.sql.Timestamp ts) {
                result.put("preSaleSchedule", PreSaleSchedule.compute(
                    ts.toInstant().atOffset(java.time.ZoneOffset.UTC)));
            }
        }

        return result;
    }

    public Map<String, Object> getTicketsByEvent(UUID eventId) {
        List<Map<String, Object>> rows = ticketInternalClient.getTicketTypesByEvent(eventId);
        return Map.of("ticketTypes", rows);
    }

    public Map<String, Object> getTicketAvailability(UUID ticketTypeId) {
        return ticketInternalClient.getTicketTypeAvailability(ticketTypeId);
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
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Event not found");
        }

        Map<String, Object> event = eventRows.getFirst();
        if (event.get("seat_layout_id") == null) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "This event does not have seat selection");
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

    public Map<String, Object> getEventQueueInfo(UUID eventId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT title, artist_name AS artist FROM events WHERE id = ?", eventId);
        if (rows.isEmpty()) {
            return Map.of("title", "Unknown", "artist", "Unknown");
        }
        return rows.getFirst();
    }
}
