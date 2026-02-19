package guru.urr.statsservice.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardStatsService {

    private final JdbcTemplate jdbcTemplate;

    public DashboardStatsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> overview() {
        return StatsQueryHelper.firstRow(jdbcTemplate, """
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
        int safeDays = StatsQueryHelper.clamp(days, 1, 365, 30);
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
}
