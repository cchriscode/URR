package guru.urr.catalogservice.domain.admin.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.catalogservice.domain.admin.dto.AdminEventRequest;
import guru.urr.catalogservice.domain.admin.dto.AdminTicketTypeRequest;
import guru.urr.catalogservice.shared.client.AuthInternalClient;
import guru.urr.catalogservice.shared.client.TicketInternalClient;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TicketInternalClient ticketInternalClient;
    @Mock private AuthInternalClient authInternalClient;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(jdbcTemplate, ticketInternalClient, authInternalClient);
    }

    @Test
    void createEvent_success() {
        UUID eventId = UUID.randomUUID();
        String adminUserId = UUID.randomUUID().toString();

        OffsetDateTime now = OffsetDateTime.now();
        AdminEventRequest request = new AdminEventRequest(
                "Test Concert", "Description", "Seoul Arena", "Seoul",
                now.plusDays(30), now.plusDays(1), now.plusDays(15),
                "https://image.url/poster.jpg", "Artist Name",
                null, null);

        when(jdbcTemplate.queryForObject(contains("INSERT INTO events"), eq(UUID.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(eventId);

        Map<String, Object> savedEvent = new HashMap<>();
        savedEvent.put("id", eventId);
        savedEvent.put("title", "Test Concert");
        savedEvent.put("status", "upcoming");

        when(jdbcTemplate.queryForList(eq("SELECT * FROM events WHERE id = ?"), eq(eventId)))
                .thenReturn(List.of(savedEvent));

        Map<String, Object> result = adminService.createEvent(request, adminUserId);

        assertEquals("Event created successfully", result.get("message"));
        assertNotNull(result.get("event"));
    }

    @Test
    void createEvent_withTicketTypes() {
        UUID eventId = UUID.randomUUID();
        String adminUserId = UUID.randomUUID().toString();

        OffsetDateTime now = OffsetDateTime.now();
        List<AdminTicketTypeRequest> ticketTypes = List.of(
                new AdminTicketTypeRequest("VIP", 150000, 100, "VIP seat"),
                new AdminTicketTypeRequest("Regular", 80000, 500, "Regular seat"));

        AdminEventRequest request = new AdminEventRequest(
                "Concert", "Desc", "Venue", "Address",
                now.plusDays(30), now.plusDays(1), now.plusDays(15),
                null, "Artist", null, ticketTypes);

        when(jdbcTemplate.queryForObject(contains("INSERT INTO events"), eq(UUID.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(eventId);
        when(jdbcTemplate.queryForList(eq("SELECT * FROM events WHERE id = ?"), eq(eventId)))
                .thenReturn(List.of(Map.of("id", eventId, "title", "Concert")));

        adminService.createEvent(request, adminUserId);

        verify(ticketInternalClient).createTicketType(eq(eventId), eq("VIP"), eq(150000), eq(100), eq("VIP seat"));
        verify(ticketInternalClient).createTicketType(eq(eventId), eq("Regular"), eq(80000), eq(500), eq("Regular seat"));
    }

    @Test
    void createEvent_invalidDates_throws() {
        OffsetDateTime now = OffsetDateTime.now();
        AdminEventRequest request = new AdminEventRequest(
                "Concert", "Desc", "Venue", "Address",
                now.plusDays(30),
                now.plusDays(15),  // saleStart AFTER saleEnd
                now.plusDays(1),   // saleEnd BEFORE saleStart
                null, "Artist", null, null);

        assertThrows(ResponseStatusException.class,
                () -> adminService.createEvent(request, UUID.randomUUID().toString()));
    }

    @Test
    void createEvent_withSeatLayout_generatesSeats() {
        UUID eventId = UUID.randomUUID();
        UUID layoutId = UUID.randomUUID();
        String adminUserId = UUID.randomUUID().toString();

        OffsetDateTime now = OffsetDateTime.now();
        AdminEventRequest request = new AdminEventRequest(
                "Concert", "Desc", "Venue", "Address",
                now.plusDays(30), now.plusDays(1), now.plusDays(15),
                null, "Artist", layoutId, null);

        when(jdbcTemplate.queryForObject(contains("INSERT INTO events"), eq(UUID.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(eventId);
        when(jdbcTemplate.queryForList(eq("SELECT * FROM events WHERE id = ?"), eq(eventId)))
                .thenReturn(List.of(Map.of("id", eventId)));

        adminService.createEvent(request, adminUserId);

        verify(ticketInternalClient).generateSeats(eventId, layoutId);
    }

    @Test
    void cancelEvent_success() {
        UUID eventId = UUID.randomUUID();

        Map<String, Object> cancelledEvent = new HashMap<>();
        cancelledEvent.put("id", eventId);
        cancelledEvent.put("title", "Concert");
        cancelledEvent.put("status", "cancelled");

        when(jdbcTemplate.queryForList(contains("UPDATE events"), eq(eventId)))
                .thenReturn(List.of(cancelledEvent));
        when(ticketInternalClient.cancelReservationsByEvent(eventId))
                .thenReturn(5);

        Map<String, Object> result = adminService.cancelEvent(eventId);

        assertEquals(5, result.get("cancelledReservations"));
        assertTrue(((String) result.get("message")).contains("cancelled"));
    }

    @Test
    void cancelEvent_notFound_throws() {
        UUID eventId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(contains("UPDATE events"), eq(eventId)))
                .thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class,
                () -> adminService.cancelEvent(eventId));
    }

    @Test
    void dashboardStats_success() {
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM events"), eq(Integer.class)))
                .thenReturn(10);
        when(ticketInternalClient.getReservationStats())
                .thenReturn(Map.of("totalReservations", 50, "totalRevenue", 5000000, "todayReservations", 3));
        when(ticketInternalClient.getRecentReservations())
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = adminService.dashboardStats();

        assertNotNull(result.get("stats"));
        assertNotNull(result.get("recentReservations"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) result.get("stats");
        assertEquals(10, stats.get("totalEvents"));
        assertEquals(50, stats.get("totalReservations"));
    }
}
