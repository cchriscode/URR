package guru.urr.statsservice.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class EventStatsService {

    private final JdbcTemplate jdbcTemplate;

    public EventStatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> events(int limit, String sortBy) {
        int safeLimit = StatsQueryHelper.clamp(limit, 1, 100, 10);
        String orderBy = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "reservations" -> "total_reservations DESC";
            case "occupancy" -> "CASE WHEN total_seats = 0 THEN 0 ELSE reserved_seats::numeric / total_seats END DESC";
            default -> "total_revenue DESC";
        };

        return jdbcTemplate.queryForList("""
            SELECT
              event_id AS id,
              ('Event ' || LEFT(CAST(event_id AS TEXT), 8)) AS title,
              NULL::TEXT AS venue,
              NOW() AS event_date,
              CASE WHEN available_seats > 0 THEN 'on_sale' ELSE 'ended' END AS status,
              total_reservations AS reservations,
              confirmed_reservations,
              total_revenue AS revenue,
              total_seats,
              reserved_seats AS sold_seats,
              ROUND(
                CASE
                  WHEN total_seats = 0 THEN 0
                  ELSE (reserved_seats::numeric / total_seats) * 100
                END,
                2
              ) AS occupancy_rate
            FROM event_stats
            ORDER BY %s
            LIMIT ?
            """.formatted(orderBy), safeLimit);
    }

    public Map<String, Object> eventById(UUID eventId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT
              event_id AS id,
              ('Event ' || LEFT(CAST(event_id AS TEXT), 8)) AS title,
              NULL::TEXT AS venue,
              NOW() AS event_date,
              CASE WHEN available_seats > 0 THEN 'on_sale' ELSE 'ended' END AS status,
              total_reservations,
              confirmed_reservations,
              GREATEST(total_reservations - confirmed_reservations, 0) AS pending_reservations,
              GREATEST(total_reservations - confirmed_reservations, 0) AS cancelled_reservations,
              total_revenue AS total_revenue,
              total_seats,
              reserved_seats AS sold_seats,
              available_seats,
              ROUND(
                CASE
                  WHEN total_seats = 0 THEN 0
                  ELSE (reserved_seats::numeric / total_seats) * 100
                END,
                2
              ) AS occupancy_rate
            FROM event_stats
            WHERE event_id = ?
            """, eventId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }

        return Map.of("overview", rows.getFirst());
    }

    public Map<String, Object> seatPreferences(UUID eventId) {
        List<Map<String, Object>> sourceRows;
        if (eventId == null) {
            sourceRows = jdbcTemplate.queryForList("""
                SELECT event_id, total_seats, reserved_seats, average_ticket_price
                FROM event_stats
                ORDER BY reserved_seats DESC
                LIMIT 20
                """);
        } else {
            sourceRows = jdbcTemplate.queryForList("""
                SELECT event_id, total_seats, reserved_seats, average_ticket_price
                FROM event_stats
                WHERE event_id = ?
                ORDER BY reserved_seats DESC
                LIMIT 20
                """, eventId);
        }

        List<Map<String, Object>> bySection = new ArrayList<>();
        List<Map<String, Object>> byPriceTier = new ArrayList<>();
        List<Map<String, Object>> topRows = new ArrayList<>();

        for (Map<String, Object> row : sourceRows) {
            int totalSeats = StatsQueryHelper.intValue(row.get("total_seats"));
            int reservedSeats = StatsQueryHelper.intValue(row.get("reserved_seats"));
            double reservationRate = totalSeats == 0 ? 0 : StatsQueryHelper.round2((reservedSeats * 100.0) / totalSeats);
            int averagePrice = StatsQueryHelper.intValue(row.get("average_ticket_price"));

            bySection.add(Map.of(
                "section", "ALL",
                "total_seats", totalSeats,
                "reserved_seats", reservedSeats,
                "reservation_rate", reservationRate
            ));

            byPriceTier.add(Map.of(
                "price_tier", StatsQueryHelper.toPriceTier(averagePrice),
                "total_seats", totalSeats,
                "reserved_seats", reservedSeats,
                "reservation_rate", reservationRate,
                "avg_price", averagePrice
            ));

            topRows.add(Map.of(
                "row_number", "ALL",
                "total_seats", totalSeats,
                "reserved_seats", reservedSeats,
                "reservation_rate", reservationRate
            ));
        }

        return Map.of(
            "bySection", bySection,
            "byPriceTier", byPriceTier,
            "topRows", topRows
        );
    }

    public Map<String, Object> cancellations(int days) {
        int safeDays = StatsQueryHelper.clamp(days, 1, 365, 30);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        int totalCancelled = StatsQueryHelper.intValue(jdbcTemplate.queryForObject("""
            SELECT COALESCE(SUM(cancelled_reservations), 0)
            FROM daily_stats
            WHERE date >= ?
            """, Integer.class, cutoff));

        List<Map<String, Object>> hourlyPattern = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourlyPattern.add(Map.of("hour", hour, "cancellations", 0));
        }

        List<Map<String, Object>> byEvent = jdbcTemplate.queryForList("""
            SELECT
              event_id AS id,
              ('Event ' || LEFT(CAST(event_id AS TEXT), 8)) AS title,
              total_reservations,
              GREATEST(total_reservations - confirmed_reservations, 0) AS cancelled,
              ROUND(
                CASE
                  WHEN total_reservations = 0 THEN 0
                  ELSE (GREATEST(total_reservations - confirmed_reservations, 0)::numeric / total_reservations) * 100
                END,
                2
              ) AS cancellation_rate
            FROM event_stats
            WHERE total_reservations > 0
            ORDER BY cancellation_rate DESC
            LIMIT 10
            """);

        List<Map<String, Object>> dailyTrend = jdbcTemplate.queryForList("""
            SELECT
              date,
              cancelled_reservations AS cancellations
            FROM daily_stats
            WHERE date >= ?
            ORDER BY date DESC
            """, cutoff);

        return Map.of(
            "overview", Map.of(
                "total_cancelled", totalCancelled,
                "avg_hours_before_cancel", 0,
                "total_refund_amount", 0
            ),
            "hourlyPattern", hourlyPattern,
            "byEvent", byEvent,
            "dailyTrend", dailyTrend
        );
    }
}
