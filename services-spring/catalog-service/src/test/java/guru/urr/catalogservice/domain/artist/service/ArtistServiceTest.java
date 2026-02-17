package guru.urr.catalogservice.domain.artist.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
class ArtistServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private ArtistService artistService;

    @BeforeEach
    void setUp() {
        artistService = new ArtistService(jdbcTemplate);
    }

    @Test
    void listArtists_success() {
        Map<String, Object> artist = new HashMap<>();
        artist.put("id", UUID.randomUUID());
        artist.put("name", "Test Artist");
        artist.put("event_count", 3);
        artist.put("member_count", 100);

        when(jdbcTemplate.queryForList(contains("FROM artists"), eq(20), eq(0)))
                .thenReturn(List.of(artist));
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM artists"), eq(Integer.class)))
                .thenReturn(1);

        Map<String, Object> result = artistService.listArtists(1, 20);

        assertNotNull(result.get("artists"));
        assertNotNull(result.get("pagination"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artists = (List<Map<String, Object>>) result.get("artists");
        assertEquals(1, artists.size());
        assertEquals("Test Artist", artists.getFirst().get("name"));
    }

    @Test
    void listArtists_empty() {
        when(jdbcTemplate.queryForList(contains("FROM artists"), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM artists"), eq(Integer.class)))
                .thenReturn(0);

        Map<String, Object> result = artistService.listArtists(1, 20);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artists = (List<Map<String, Object>>) result.get("artists");
        assertTrue(artists.isEmpty());

        @SuppressWarnings("unchecked")
        Map<String, Object> pagination = (Map<String, Object>) result.get("pagination");
        assertEquals(0, pagination.get("total"));
    }

    @Test
    void listArtists_clampsPagination() {
        when(jdbcTemplate.queryForList(contains("FROM artists"), eq(100), eq(0)))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM artists"), eq(Integer.class)))
                .thenReturn(0);

        // limit > 100 should be clamped to 100, page < 1 should be clamped to 1
        Map<String, Object> result = artistService.listArtists(0, 999);

        @SuppressWarnings("unchecked")
        Map<String, Object> pagination = (Map<String, Object>) result.get("pagination");
        assertEquals(1, pagination.get("page"));
        assertEquals(100, pagination.get("limit"));
    }

    @Test
    void getArtistDetail_success() {
        UUID artistId = UUID.randomUUID();
        Map<String, Object> artist = new HashMap<>();
        artist.put("id", artistId);
        artist.put("name", "Test Artist");

        when(jdbcTemplate.queryForList(eq("SELECT * FROM artists WHERE id = ?"), eq(artistId)))
                .thenReturn(List.of(artist));
        when(jdbcTemplate.queryForObject(contains("COUNT(*) FROM artist_memberships"), eq(Integer.class), eq(artistId)))
                .thenReturn(50);
        when(jdbcTemplate.queryForList(contains("FROM events e"), eq(artistId)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = artistService.getArtistDetail(artistId);

        assertEquals(artist, result.get("artist"));
        assertEquals(50, result.get("memberCount"));
        assertNotNull(result.get("events"));
    }

    @Test
    void getArtistDetail_notFound_throws() {
        UUID artistId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(eq("SELECT * FROM artists WHERE id = ?"), eq(artistId)))
                .thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class,
                () -> artistService.getArtistDetail(artistId));
    }
}
