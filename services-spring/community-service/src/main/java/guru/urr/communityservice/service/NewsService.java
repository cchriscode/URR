package guru.urr.communityservice.service;

import guru.urr.communityservice.dto.NewsCreateRequest;
import guru.urr.communityservice.dto.NewsUpdateRequest;
import guru.urr.communityservice.shared.client.TicketInternalClient;
import guru.urr.communityservice.shared.security.AuthUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TicketInternalClient ticketInternalClient;

    public NewsService(JdbcTemplate jdbcTemplate, TicketInternalClient ticketInternalClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
    }

    public Map<String, Object> list(Integer page, Integer limit) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        int offset = (safePage - 1) * safeLimit;

        List<Map<String, Object>> news = jdbcTemplate.queryForList("""
            SELECT id, title, content, author, author_id, views, is_pinned, created_at, updated_at
            FROM news
            ORDER BY is_pinned DESC, created_at DESC
            LIMIT ? OFFSET ?
            """, safeLimit, offset);

        Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM news", Integer.class);
        int safeTotal = total != null ? total : 0;

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", safePage);
        pagination.put("limit", safeLimit);
        pagination.put("total", safeTotal);
        pagination.put("totalPages", (int) Math.ceil((double) safeTotal / safeLimit));

        return Map.of("news", news, "pagination", pagination);
    }

    @Transactional
    public Map<String, Object> detail(UUID id) {
        jdbcTemplate.update("UPDATE news SET views = views + 1 WHERE id = ?", id);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, title, content, author, author_id, views, is_pinned, created_at, updated_at
            FROM news
            WHERE id = ?
            """, id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found");
        }
        return Map.of("news", rows.getFirst());
    }

    @Transactional
    public Map<String, Object> create(NewsCreateRequest request, AuthUser user) {
        // Always use the authenticated user's ID to prevent author_id spoofing
        UUID authorId = UUID.fromString(user.userId());
        boolean isPinned = request.isPinned() != null && request.isPinned();

        Map<String, Object> row = jdbcTemplate.queryForList("""
            INSERT INTO news (title, content, author, author_id, is_pinned)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id, title, content, author, author_id, views, is_pinned, created_at, updated_at
            """, request.title(), request.content(), request.author(), authorId, isPinned)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create news"));

        try {
            ticketInternalClient.awardMembershipPoints(
                authorId.toString(), "NEWS_CREATION", 30,
                "News post created: " + request.title(), (UUID) row.get("id"));
        } catch (Exception e) {
            log.warn("Failed to award membership points for news creation: {}", e.getMessage());
        }

        return Map.of("news", row);
    }

    @Transactional
    public Map<String, Object> update(UUID id, NewsUpdateRequest request, AuthUser user) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT author_id FROM news WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found");
        }

        String ownerId = String.valueOf(rows.getFirst().get("author_id"));
        if (!ownerId.equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can edit this news");
        }

        Map<String, Object> row;
        if (request.isPinned() != null) {
            row = jdbcTemplate.queryForList("""
                UPDATE news
                SET title = ?, content = ?, is_pinned = ?, updated_at = NOW()
                WHERE id = ?
                RETURNING id, title, content, author, author_id, views, is_pinned, created_at, updated_at
                """, request.title(), request.content(), request.isPinned(), id).getFirst();
        } else {
            row = jdbcTemplate.queryForList("""
                UPDATE news
                SET title = ?, content = ?, updated_at = NOW()
                WHERE id = ?
                RETURNING id, title, content, author, author_id, views, is_pinned, created_at, updated_at
                """, request.title(), request.content(), id).getFirst();
        }

        return Map.of("news", row);
    }

    @Transactional
    public Map<String, Object> delete(UUID id, AuthUser user) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT author_id FROM news WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found");
        }

        String ownerId = String.valueOf(rows.getFirst().get("author_id"));
        if (!user.isAdmin() && !ownerId.equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Delete permission denied");
        }

        jdbcTemplate.update("DELETE FROM news WHERE id = ?", id);
        return Map.of("message", "News deleted successfully");
    }
}
