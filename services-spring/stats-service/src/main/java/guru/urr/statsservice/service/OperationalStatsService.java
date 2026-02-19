package guru.urr.statsservice.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OperationalStatsService {

    private final JdbcTemplate jdbcTemplate;

    public OperationalStatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> revenue(String period, int days) {
        int safeDays = StatsQueryHelper.clamp(days, 1, 365, 30);
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

    public Map<String, Object> hourlyTraffic(int days) {
        int safeDays = StatsQueryHelper.clamp(days, 1, 90, 7);
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

    public Map<String, Object> realtime() {
        Map<String, Object> latestDaily = StatsQueryHelper.firstRow(jdbcTemplate, """
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

        int total = StatsQueryHelper.intValue(latestDaily.get("total_reservations"));
        int confirmed = StatsQueryHelper.intValue(latestDaily.get("confirmed_reservations"));
        int cancelled = StatsQueryHelper.intValue(latestDaily.get("cancelled_reservations"));
        int activeUsers = StatsQueryHelper.intValue(latestDaily.get("active_users"));

        int activePayments = Math.max(total - confirmed - cancelled, 0);
        int lastHourReservations = Math.max(Math.round(total / StatsQueryHelper.HOURS_IN_DAY), 0);
        int lastHourRevenue = Math.max(Math.round(StatsQueryHelper.intValue(latestDaily.get("total_revenue")) / StatsQueryHelper.HOURS_IN_DAY), 0);

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

    public Map<String, Object> performance() {
        int users = StatsQueryHelper.intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(new_users), 0) FROM daily_stats", Integer.class));
        int events = StatsQueryHelper.intValue(jdbcTemplate.queryForObject("SELECT COALESCE(COUNT(*), 0) FROM event_stats", Integer.class));
        int reservations = StatsQueryHelper.intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(total_reservations), 0) FROM daily_stats", Integer.class));
        int seats = StatsQueryHelper.intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(total_seats), 0) FROM event_stats", Integer.class));
        int payments = StatsQueryHelper.intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(confirmed_reservations), 0) FROM daily_stats", Integer.class));

        Map<String, Object> recent = StatsQueryHelper.firstRow(jdbcTemplate, """
            SELECT
              COALESCE(SUM(total_reservations), 0) AS total,
              COALESCE(SUM(confirmed_reservations), 0) AS successful
            FROM daily_stats
            WHERE date >= ?
            """, LocalDate.now().minusDays(7));

        int totalRecent = StatsQueryHelper.intValue(recent.get("total"));
        int successfulRecent = StatsQueryHelper.intValue(recent.get("successful"));
        double successRate = StatsQueryHelper.toRate(totalRecent, successfulRecent);

        List<Map<String, Object>> loadPattern = new ArrayList<>();
        double avgPerHour = totalRecent / 168.0;
        for (int hour = 0; hour < 24; hour++) {
            loadPattern.add(Map.of(
                "hour", hour,
                "request_count", (int) Math.round(avgPerHour * 7),
                "avg_per_day", StatsQueryHelper.round2(avgPerHour)
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
}
