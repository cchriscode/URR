package guru.urr.catalogservice.domain.event.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.catalogservice.shared.client.TicketInternalClient;
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
class EventReadServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TicketInternalClient ticketInternalClient;

    private EventReadService eventReadService;

    @BeforeEach
    void setUp() {
        eventReadService = new EventReadService(jdbcTemplate, ticketInternalClient);
    }

    @Test
    void listEvents_success() {
        Map<String, Object> event = new HashMap<>();
        event.put("id", UUID.randomUUID());
        event.put("title", "Test Concert");
        event.put("status", "on_sale");

        when(jdbcTemplate.queryForList(contains("SELECT"), any(Object[].class)))
                .thenReturn(List.of(event));
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        Map<String, Object> result = eventReadService.listEvents(null, null, 1, 20);

        assertNotNull(result.get("events"));
        assertNotNull(result.get("pagination"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");
        assertEquals(1, events.size());
        assertEquals("Test Concert", events.getFirst().get("title"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pagination = (Map<String, Object>) result.get("pagination");
        assertEquals(1, pagination.get("page"));
        assertEquals(1, pagination.get("total"));
    }

    @Test
    void listEvents_withStatusFilter() {
        when(jdbcTemplate.queryForList(contains("e.status = ?"), any(Object[].class)))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        Map<String, Object> result = eventReadService.listEvents("on_sale", null, 1, 20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");
        assertTrue(events.isEmpty());
    }

    @Test
    void listEvents_withSearchQuery() {
        when(jdbcTemplate.queryForList(contains("ILIKE"), any(Object[].class)))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT"), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        Map<String, Object> result = eventReadService.listEvents(null, "concert", 1, 20);

        assertNotNull(result.get("events"));
    }

    @Test
    void getEventDetail_success() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("id", eventId);
        event.put("title", "Test Concert");
        event.put("artist_id", null);

        when(jdbcTemplate.queryForList(eq("SELECT * FROM events WHERE id = ?"), eq(eventId)))
                .thenReturn(List.of(event));
        when(ticketInternalClient.getTicketTypesByEvent(eventId))
                .thenReturn(List.of(Map.of("id", UUID.randomUUID(), "name", "VIP", "price", 100000)));

        Map<String, Object> result = eventReadService.getEventDetail(eventId);

        assertNotNull(result.get("event"));
        assertNotNull(result.get("ticketTypes"));
    }

    @Test
    void getEventDetail_notFound_throws() {
        UUID eventId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(eq("SELECT * FROM events WHERE id = ?"), eq(eventId)))
                .thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class,
                () -> eventReadService.getEventDetail(eventId));
    }

    @Test
    void getEventQueueInfo_success() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("title", "Concert");
        row.put("artist", "Artist Name");

        when(jdbcTemplate.queryForList(contains("SELECT title"), eq(eventId)))
                .thenReturn(List.of(row));

        Map<String, Object> result = eventReadService.getEventQueueInfo(eventId);

        assertEquals("Concert", result.get("title"));
    }

    @Test
    void getEventQueueInfo_notFound_returnsDefault() {
        UUID eventId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(contains("SELECT title"), eq(eventId)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = eventReadService.getEventQueueInfo(eventId);

        assertEquals("Unknown", result.get("title"));
    }
}
