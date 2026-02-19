package guru.urr.catalogservice.domain.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import guru.urr.catalogservice.shared.client.AuthInternalClient;
import guru.urr.catalogservice.shared.client.TicketInternalClient;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TicketInternalClient ticketInternalClient;
    @Mock private AuthInternalClient authInternalClient;

    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
        adminDashboardService = new AdminDashboardService(jdbcTemplate, ticketInternalClient, authInternalClient);
    }

    @Test
    void dashboardStats_success() {
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM events"), eq(Integer.class)))
                .thenReturn(10);
        when(ticketInternalClient.getReservationStats())
                .thenReturn(Map.of("totalReservations", 50, "totalRevenue", 5000000, "todayReservations", 3));
        when(ticketInternalClient.getRecentReservations())
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = adminDashboardService.dashboardStats();

        assertNotNull(result.get("stats"));
        assertNotNull(result.get("recentReservations"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) result.get("stats");
        assertEquals(10, stats.get("totalEvents"));
        assertEquals(50, stats.get("totalReservations"));
    }
}
