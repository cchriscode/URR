package guru.urr.communityservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.communityservice.dto.NewsCreateRequest;
import guru.urr.communityservice.shared.client.TicketInternalClient;
import guru.urr.common.security.AuthUser;
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
class NewsServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TicketInternalClient ticketInternalClient;

    private NewsService newsService;

    @BeforeEach
    void setUp() {
        newsService = new NewsService(jdbcTemplate, ticketInternalClient);
    }

    @Test
    void create_success() {
        UUID authorId = UUID.randomUUID();
        UUID newsId = UUID.randomUUID();
        AuthUser user = new AuthUser(authorId.toString(), "user@test.com", "user");
        NewsCreateRequest request = new NewsCreateRequest(
                "Test Title", "Test Content", "Author Name", authorId, false);

        Map<String, Object> insertedRow = new HashMap<>();
        insertedRow.put("id", newsId);
        insertedRow.put("title", "Test Title");
        insertedRow.put("content", "Test Content");
        insertedRow.put("author", "Author Name");
        insertedRow.put("author_id", authorId);
        insertedRow.put("views", 0);
        insertedRow.put("is_pinned", false);
        insertedRow.put("created_at", "2026-02-12T00:00:00");
        insertedRow.put("updated_at", "2026-02-12T00:00:00");

        doReturn(List.of(insertedRow))
                .when(jdbcTemplate).queryForList(
                        contains("INSERT INTO news"),
                        eq("Test Title"), eq("Test Content"), eq("Author Name"),
                        eq(authorId), eq(false));

        Map<String, Object> result = newsService.create(request, user);

        assertNotNull(result.get("news"));
        @SuppressWarnings("unchecked")
        Map<String, Object> news = (Map<String, Object>) result.get("news");
        assertEquals(newsId, news.get("id"));
        assertEquals("Test Title", news.get("title"));

        verify(ticketInternalClient).awardMembershipPoints(
                eq(authorId.toString()), eq("NEWS_CREATION"), eq(30),
                contains("Test Title"), eq(newsId));
    }

    @Test
    void list_success() {
        Map<String, Object> newsItem = new HashMap<>();
        newsItem.put("id", UUID.randomUUID());
        newsItem.put("title", "News 1");
        newsItem.put("content", "Content 1");
        newsItem.put("author", "Author");
        newsItem.put("author_id", UUID.randomUUID());
        newsItem.put("views", 10);
        newsItem.put("is_pinned", false);

        when(jdbcTemplate.queryForList(contains("SELECT id, title, content"), anyInt(), anyInt()))
                .thenReturn(List.of(newsItem));
        when(jdbcTemplate.queryForObject(contains("SELECT COUNT"), eq(Integer.class)))
                .thenReturn(1);

        Map<String, Object> result = newsService.list(1, 20);

        assertNotNull(result.get("news"));
        assertNotNull(result.get("pagination"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> newsList = (List<Map<String, Object>>) result.get("news");
        assertEquals(1, newsList.size());
        assertEquals("News 1", newsList.getFirst().get("title"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pagination = (Map<String, Object>) result.get("pagination");
        assertEquals(1, pagination.get("page"));
        assertEquals(1, pagination.get("total"));
    }

    @Test
    void getById_notFound_throws() {
        UUID newsId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(contains("SELECT id, title, content"), eq(newsId)))
                .thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class,
                () -> newsService.detail(newsId));
    }
}
