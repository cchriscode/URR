package guru.urr.statsservice.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserStatsService {

    private final JdbcTemplate jdbcTemplate;

    public UserStatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> users(int days) {
        int safeDays = StatsQueryHelper.clamp(days, 1, 365, 30);
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

        int totalUsers = StatsQueryHelper.intValue(jdbcTemplate.queryForObject("SELECT COALESCE(SUM(new_users), 0) FROM daily_stats", Integer.class));
        return Map.of(
            "daily", daily,
            "byRole", List.of(Map.of("role", "user", "count", totalUsers))
        );
    }

    public Map<String, Object> userBehavior(int days) {
        int safeDays = StatsQueryHelper.clamp(days, 1, 365, 30);
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
            int newUsers = StatsQueryHelper.intValue(row.get("new_users"));
            int activeUsers = StatsQueryHelper.intValue(row.get("active_users"));
            int reservations = StatsQueryHelper.intValue(row.get("total_reservations"));
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
        double avgReservations = totalNewUsers == 0 ? 0 : StatsQueryHelper.round2((double) totalReservations / totalNewUsers);

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

    public Map<String, Object> conversion(int days) {
        int safeDays = StatsQueryHelper.clamp(days, 1, 365, 30);
        LocalDate cutoff = LocalDate.now().minusDays(safeDays);

        Map<String, Object> totals = StatsQueryHelper.firstRow(jdbcTemplate, """
            SELECT
              COALESCE(SUM(total_reservations), 0) AS total,
              COALESCE(SUM(confirmed_reservations), 0) AS completed,
              COALESCE(SUM(cancelled_reservations), 0) AS cancelled
            FROM daily_stats
            WHERE date >= ?
            """, cutoff);

        int total = StatsQueryHelper.intValue(totals.get("total"));
        int completed = StatsQueryHelper.intValue(totals.get("completed"));
        int cancelled = StatsQueryHelper.intValue(totals.get("cancelled"));
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
                "conversion_rate", StatsQueryHelper.toRate(total, completed),
                "cancellation_rate", StatsQueryHelper.toRate(total, cancelled),
                "pending_rate", StatsQueryHelper.toRate(total, pending)
            ),
            "dailyTrend", dailyTrend
        );
    }
}
