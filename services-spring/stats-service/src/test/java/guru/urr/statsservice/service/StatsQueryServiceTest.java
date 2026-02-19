package guru.urr.statsservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class StatsQueryServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private DashboardStatsService dashboardStatsService;
    private EventStatsService eventStatsService;
    private UserStatsService userStatsService;

    @BeforeEach
    void setUp() {
        dashboardStatsService = new DashboardStatsService(jdbcTemplate);
        eventStatsService = new EventStatsService(jdbcTemplate);
        userStatsService = new UserStatsService(jdbcTemplate);
    }

    @Test
    void overview_returnsAggregatedStats() {
        Map<String, Object> row = Map.of(
            "total_users", 100,
            "active_events", 5,
            "total_reservations", 200,
            "confirmed_reservations", 150,
            "total_revenue", 7500000,
            "completed_payments", 150,
            "payment_revenue", 7500000
        );
        when(jdbcTemplate.queryForList(contains("daily_stats"), any(Object[].class))).thenReturn(List.of(row));

        Map<String, Object> result = dashboardStatsService.overview();

        assertEquals(100, result.get("total_users"));
        assertEquals(5, result.get("active_events"));
    }

    @Test
    void overview_emptyTable_returnsEmptyMap() {
        when(jdbcTemplate.queryForList(contains("daily_stats"), any(Object[].class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = dashboardStatsService.overview();

        assertTrue(result.isEmpty());
    }

    @Test
    void daily_returnsListOfDailyStats() {
        Map<String, Object> day = Map.of("date", "2026-02-12", "reservations", 10, "confirmed", 8, "cancelled", 1, "revenue", 500000);
        when(jdbcTemplate.queryForList(contains("daily_stats"), any(Object[].class))).thenReturn(List.of(day));

        List<Map<String, Object>> result = dashboardStatsService.daily(7);

        assertFalse(result.isEmpty());
        assertEquals("2026-02-12", result.getFirst().get("date"));
    }

    @Test
    void events_returnsEventStats() {
        Map<String, Object> event = Map.of("id", UUID.randomUUID(), "title", "Test Event", "revenue", 1000000);
        when(jdbcTemplate.queryForList(contains("event_stats"), any(Object[].class))).thenReturn(List.of(event));

        List<Map<String, Object>> result = eventStatsService.events(10, "revenue");

        assertFalse(result.isEmpty());
    }

    @Test
    void conversion_returnsFunnelData() {
        Map<String, Object> totals = Map.of("total", 100, "completed", 80, "cancelled", 10);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(totals));

        Map<String, Object> result = userStatsService.conversion(30);

        assertNotNull(result.get("funnel"));
        assertNotNull(result.get("rates"));
    }
}
