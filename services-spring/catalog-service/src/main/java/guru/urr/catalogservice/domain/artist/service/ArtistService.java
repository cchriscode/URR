package guru.urr.catalogservice.domain.artist.service;

import guru.urr.catalogservice.shared.util.PreSaleSchedule;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArtistService {

    private final JdbcTemplate jdbcTemplate;

    public ArtistService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> listArtists(int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int offset = (safePage - 1) * safeLimit;

        List<Map<String, Object>> artists = jdbcTemplate.queryForList("""
            SELECT a.id, a.name, a.image_url, a.description, a.membership_price, a.created_at,
                   COUNT(DISTINCT e.id) AS event_count,
                   (SELECT COUNT(*) FROM artist_memberships am
                    WHERE am.artist_id = a.id AND am.status = 'active') AS member_count
            FROM artists a
            LEFT JOIN events e ON e.artist_id = a.id
            GROUP BY a.id
            ORDER BY a.name ASC
            LIMIT ? OFFSET ?
            """, safeLimit, offset);

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM artists", Integer.class);
        int safeTotal = total != null ? total : 0;

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", safePage);
        pagination.put("limit", safeLimit);
        pagination.put("total", safeTotal);
        pagination.put("totalPages", (int) Math.ceil((double) safeTotal / safeLimit));

        return Map.of("artists", artists, "pagination", pagination);
    }

    public Map<String, Object> getArtistDetail(UUID artistId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM artists WHERE id = ?", artistId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found");
        }

        Map<String, Object> artist = rows.getFirst();

        Integer memberCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM artist_memberships WHERE artist_id = ? AND status = 'active'",
            Integer.class, artistId);

        List<Map<String, Object>> events = jdbcTemplate.queryForList("""
            SELECT e.id, e.title, e.venue, e.event_date, e.sale_start_date, e.sale_end_date,
                   e.status, e.poster_image_url,
                   MIN(tt.price) AS min_price, MAX(tt.price) AS max_price
            FROM events e
            LEFT JOIN ticket_types tt ON e.id = tt.event_id
            WHERE e.artist_id = ?
            GROUP BY e.id
            ORDER BY e.event_date ASC
            """, artistId);

        List<Map<String, Object>> eventsWithSchedule = events.stream().map(event -> {
            Map<String, Object> e = new HashMap<>(event);
            Object saleStart = event.get("sale_start_date");
            if (saleStart instanceof OffsetDateTime odt) {
                e.put("pre_sale_schedule", PreSaleSchedule.compute(odt));
            } else if (saleStart instanceof java.sql.Timestamp ts) {
                e.put("pre_sale_schedule", PreSaleSchedule.compute(
                    ts.toInstant().atOffset(java.time.ZoneOffset.UTC)));
            }
            return e;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("artist", artist);
        result.put("memberCount", memberCount != null ? memberCount : 0);
        result.put("events", eventsWithSchedule);
        return result;
    }
}
