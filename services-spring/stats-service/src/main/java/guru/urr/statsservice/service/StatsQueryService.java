package guru.urr.statsservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StatsQueryService {

    private final JdbcTemplate jdbcTemplate;

    public StatsQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> overview() {
        return firstRow("""
            SELECT
              COALESCE(SUM(new_users), 0) AS total_users,
              COALESCE(MAX(active_events), 0) AS active_events,
              COALESCE(SUM(total_reservations), 0) AS total_reservations,
              COALESCE(SUM(confirmed_reservations), 0) AS confirmed_reservations,
              COALESCE(SUM(total_revenue), 0) AS total_revenue,
              COALESCE(SUM(confirmed_reservations), 0) AS completed_payments,
              COALESCE(SUM(payment_revenue), 0) AS payment_revenue
            FROM daily_stats
            """);
    }

    public List<Map<String, Object>> daily(int days) {
        int safeDays = clamp(days, 1, 365, 30);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);
        return jdbcTemplate.queryForList("""
            SELECT
              date,
              total_reservations AS reservations,
              confirmed_reservations AS confirmed,
              cancelled_reservations AS cancelled,
              total_revenue AS revenue
            FROM daily_stats
            WHERE date >= ?
            ORDER BY date DESC
            """, cutoff);
    }

    public List<Map<String, Object>> events(int limit, String sortBy) {
        int safeLimit = clamp(limit, 1, 100, 10);
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

        List<Map<String, Object>> dailyTrend = jdbcTemplate.queryForList("""
            SELECT
              date,
              total_reservations AS reservations,
              total_revenue AS revenue
            FROM daily_stats
            ORDER BY date ASC
            LIMIT 30
            """);

        return Map.of(
            "overview", rows.getFirst(),
            "dailyTrend", dailyTrend
        );
    }

    public List<Map<String, Object>> payments() {
        return jdbcTemplate.queryForList("""
            SELECT
              'aggregated' AS method,
              COALESCE(SUM(confirmed_reservations), 0) AS count,
              COALESCE(SUM(payment_revenue), 0) AS total_amount,
              CASE
                WHEN COALESCE(SUM(confirmed_reservations), 0) = 0 THEN 0
                ELSE ROUND(COALESCE(SUM(payment_revenue), 0)::numeric / NULLIF(SUM(confirmed_reservations), 0), 2)
              END AS average_amount
            FROM daily_stats
            """);
    }

    public List<Map<String, Object>> revenue(String period, int days) {
        int safeDays = clamp(days, 1, 365, 30);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        String groupBy = switch (period == null ? "" : period.toLowerCase()) {
            case "weekly" -> "DATE_TRUNC('week', date)::date";
            case "monthly" -> "DATE_TRUNC('month', date)::date";
            default -> "date";
        };

        return jdbcTemplate.queryForList("""
            SELECT
              %s AS period,
              COALESCE(SUM(confirmed_reservations), 0) AS payment_count,
              COALESCE(SUM(payment_revenue), 0) AS total_revenue,
              CASE
                WHEN COALESCE(SUM(confirmed_reservations), 0) = 0 THEN 0
                ELSE ROUND(COALESCE(SUM(payment_revenue), 0)::numeric / NULLIF(SUM(confirmed_reservations), 0), 2)
              END AS average_amount
            FROM daily_stats
            WHERE date >= ?
            GROUP BY %s
            ORDER BY period DESC
            """.formatted(groupBy, groupBy), cutoff);
    }

    public Map<String, Object> users(int days) {
        int safeDays = clamp(days, 1, 365, 30);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        List<Map<String, Object>> daily = jdbcTemplate.queryForList("""
            SELECT
              date,
              new_users,
              SUM(new_users) OVER (ORDER BY date) AS cumulative_users
            FROM daily_stats
            WHERE date >= ?
            ORDER BY date DESC
            """, cutoff);

        int totalUsers = intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(new_users), 0) FROM daily_stats", Integer.class));
        return Map.of(
            "daily", daily,
            "byRole", List.of(Map.of("role", "user", "count", totalUsers))
        );
    }

    public Map<String, Object> hourlyTraffic(int days) {
        int safeDays = clamp(days, 1, 90, 7);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        List<Map<String, Object>> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(Map.of(
                "hour", hour,
                "total_reservations", 0,
                "confirmed", 0,
                "cancelled", 0,
                "revenue", 0
            ));
        }

        List<Map<String, Object>> weekdayComparison = jdbcTemplate.queryForList("""
            SELECT
              CASE WHEN EXTRACT(DOW FROM date) IN (0, 6) THEN 'weekend' ELSE 'weekday' END AS day_type,
              COALESCE(SUM(total_reservations), 0) AS reservations,
              CASE
                WHEN COALESCE(SUM(confirmed_reservations), 0) = 0 THEN 0
                ELSE ROUND(COALESCE(SUM(total_revenue), 0)::numeric / NULLIF(SUM(confirmed_reservations), 0), 2)
              END AS avg_amount
            FROM daily_stats
            WHERE date >= ?
            GROUP BY day_type
            ORDER BY day_type
            """, cutoff);

        return Map.of(
            "hourly", hourly,
            "weekdayComparison", weekdayComparison,
            "peakHour", Map.of("hour", 0, "reservations", 0)
        );
    }

    public Map<String, Object> conversion(int days) {
        int safeDays = clamp(days, 1, 365, 30);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        Map<String, Object> totals = firstRow("""
            SELECT
              COALESCE(SUM(total_reservations), 0) AS total,
              COALESCE(SUM(confirmed_reservations), 0) AS completed,
              COALESCE(SUM(cancelled_reservations), 0) AS cancelled
            FROM daily_stats
            WHERE date >= ?
            """, cutoff);

        int total = intValue(totals.get("total"));
        int completed = intValue(totals.get("completed"));
        int cancelled = intValue(totals.get("cancelled"));
        int pending = Math.max(total - completed - cancelled, 0);

        List<Map<String, Object>> dailyTrend = jdbcTemplate.queryForList("""
            SELECT
              date,
              total_reservations AS total,
              confirmed_reservations AS completed,
              ROUND(
                CASE
                  WHEN total_reservations = 0 THEN 0
                  ELSE (confirmed_reservations::numeric / total_reservations) * 100
                END,
                2
              ) AS conversion_rate
            FROM daily_stats
            WHERE date >= ?
            ORDER BY date DESC
            """, cutoff);

        return Map.of(
            "funnel", Map.of(
                "total_started", total,
                "completed", completed,
                "cancelled", cancelled,
                "pending", pending
            ),
            "rates", Map.of(
                "conversion_rate", toRate(total, completed),
                "cancellation_rate", toRate(total, cancelled),
                "pending_rate", toRate(total, pending)
            ),
            "dailyTrend", dailyTrend
        );
    }

    public Map<String, Object> cancellations(int days) {
        int safeDays = clamp(days, 1, 365, 30);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        int totalCancelled = intValue(jdbcTemplate.queryForObject("""
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

    public Map<String, Object> realtime() {
        Map<String, Object> latestDaily = firstRow("""
            SELECT
              COALESCE(total_reservations, 0) AS total_reservations,
              COALESCE(confirmed_reservations, 0) AS confirmed_reservations,
              COALESCE(cancelled_reservations, 0) AS cancelled_reservations,
              COALESCE(total_revenue, 0) AS total_revenue,
              COALESCE(active_users, 0) AS active_users
            FROM daily_stats
            ORDER BY date DESC
            LIMIT 1
            """);

        int total = intValue(latestDaily.get("total_reservations"));
        int confirmed = intValue(latestDaily.get("confirmed_reservations"));
        int cancelled = intValue(latestDaily.get("cancelled_reservations"));
        int activeUsers = intValue(latestDaily.get("active_users"));

        int activePayments = Math.max(total - confirmed - cancelled, 0);
        int lastHourReservations = Math.max(Math.round(total / 24.0f), 0);
        int lastHourRevenue = Math.max(Math.round(intValue(latestDaily.get("total_revenue")) / 24.0f), 0);

        List<Map<String, Object>> trendingEvents = jdbcTemplate.queryForList("""
            SELECT
              event_id AS id,
              ('Event ' || LEFT(CAST(event_id AS TEXT), 8)) AS title,
              total_reservations AS recent_reservations
            FROM event_stats
            ORDER BY total_reservations DESC
            LIMIT 5
            """);

        return Map.of(
            "current", Map.of(
                "locked_seats", 0,
                "active_payments", activePayments,
                "active_users", activeUsers
            ),
            "lastHour", Map.of(
                "reservations", lastHourReservations,
                "revenue", lastHourRevenue
            ),
            "recentActivity", List.of(),
            "trendingEvents", trendingEvents
        );
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
            int totalSeats = intValue(row.get("total_seats"));
            int reservedSeats = intValue(row.get("reserved_seats"));
            double reservationRate = totalSeats == 0 ? 0 : round2((reservedSeats * 100.0) / totalSeats);
            int averagePrice = intValue(row.get("average_ticket_price"));

            bySection.add(Map.of(
                "section", "ALL",
                "total_seats", totalSeats,
                "reserved_seats", reservedSeats,
                "reservation_rate", reservationRate
            ));

            byPriceTier.add(Map.of(
                "price_tier", toPriceTier(averagePrice),
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

    public Map<String, Object> userBehavior(int days) {
        int safeDays = clamp(days, 1, 365, 30);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        List<Map<String, Object>> dailyStats = jdbcTemplate.queryForList("""
            SELECT
              date,
              new_users,
              active_users,
              total_reservations
            FROM daily_stats
            WHERE date >= ?
            ORDER BY date DESC
            """, cutoff);

        int totalNewUsers = 0;
        int totalReservations = 0;
        int maxReservations = 0;

        List<Map<String, Object>> weekdayActivity = new ArrayList<>();
        for (Map<String, Object> row : dailyStats) {
            int newUsers = intValue(row.get("new_users"));
            int activeUsers = intValue(row.get("active_users"));
            int reservations = intValue(row.get("total_reservations"));
            totalNewUsers += newUsers;
            totalReservations += reservations;
            maxReservations = Math.max(maxReservations, reservations);

            Map<String, Object> weekdayRow = new HashMap<>();
            weekdayRow.put("day_name", row.get("date"));
            weekdayRow.put("day_of_week", 0);
            weekdayRow.put("unique_users", activeUsers);
            weekdayRow.put("reservations", reservations);
            weekdayActivity.add(weekdayRow);
        }

        int returningUsers = Math.max(totalReservations - totalNewUsers, 0);
        int loyalUsers = returningUsers / 3;
        double avgReservations = totalNewUsers == 0 ? 0 : round2((double) totalReservations / totalNewUsers);

        return Map.of(
            "userTypes", List.of(
                Map.of("user_type", "new", "user_count", totalNewUsers, "total_reservations", totalNewUsers),
                Map.of("user_type", "returning", "user_count", returningUsers, "total_reservations", returningUsers),
                Map.of("user_type", "loyal", "user_count", loyalUsers, "total_reservations", loyalUsers)
            ),
            "averageMetrics", Map.of(
                "avg_reservations", avgReservations,
                "max_reservations", maxReservations,
                "median_reservations", 0
            ),
            "spendingDistribution", List.of(
                Map.of("spending_tier", "aggregated", "user_count", Math.max(totalNewUsers, 1))
            ),
            "avgTimeToPayment", "0.00",
            "weekdayActivity", weekdayActivity
        );
    }

    public Map<String, Object> performance() {
        int users = intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(new_users), 0) FROM daily_stats", Integer.class));
        int events = intValue(jdbcTemplate.queryForObject("SELECT COALESCE(COUNT(*), 0) FROM event_stats", Integer.class));
        int reservations = intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(total_reservations), 0) FROM daily_stats", Integer.class));
        int seats = intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(total_seats), 0) FROM event_stats", Integer.class));
        int payments = intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(confirmed_reservations), 0) FROM daily_stats", Integer.class));

        Map<String, Object> recent = firstRow("""
            SELECT
              COALESCE(SUM(total_reservations), 0) AS total,
              COALESCE(SUM(confirmed_reservations), 0) AS successful
            FROM daily_stats
            WHERE date >= ?
            """, LocalDate.now().minusDays(7));

        int totalRecent = intValue(recent.get("total"));
        int successfulRecent = intValue(recent.get("successful"));
        double successRate = toRate(totalRecent, successfulRecent);

        List<Map<String, Object>> loadPattern = new ArrayList<>();
        double avgPerHour = totalRecent / 168.0;
        for (int hour = 0; hour < 24; hour++) {
            loadPattern.add(Map.of(
                "hour", hour,
                "request_count", (int) Math.round(avgPerHour * 7),
                "avg_per_day", round2(avgPerHour)
            ));
        }

        return Map.of(
            "database", Map.of(
                "size", "n/a",
                "sizeBytes", 0,
                "tableCounts", Map.of(
                    "users", users,
                    "events", events,
                    "reservations", reservations,
                    "seats", seats,
                    "payments", payments
                )
            ),
            "recentPerformance", Map.of(
                "successRate", successRate,
                "totalRequests", totalRecent
            ),
            "metrics", Map.of(
                "avgSeatLockSeconds", "0.00",
                "paymentFailureRate", 0
            ),
            "loadPattern", loadPattern
        );
    }

    private Map<String, Object> firstRow(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private int clamp(int value, int min, int max, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Long longValue) {
            return longValue.intValue();
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.intValue();
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private double toRate(int total, int part) {
        if (total <= 0) {
            return 0;
        }
        return round2((part * 100.0) / total);
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String toPriceTier(int averagePrice) {
        if (averagePrice < 50000) {
            return "budget";
        }
        if (averagePrice < 100000) {
            return "standard";
        }
        if (averagePrice < 150000) {
            return "premium";
        }
        return "vip";
    }
}