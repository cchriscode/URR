package com.tiketi.communityservice.service;

import com.tiketi.communityservice.dto.PostCreateRequest;
import com.tiketi.communityservice.dto.PostUpdateRequest;
import com.tiketi.communityservice.shared.client.TicketInternalClient;
import com.tiketi.communityservice.shared.security.AuthUser;
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
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);
    private static final int POST_POINTS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final TicketInternalClient ticketInternalClient;

    public PostService(JdbcTemplate jdbcTemplate, TicketInternalClient ticketInternalClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
    }

    public Map<String, Object> list(UUID artistId, Integer page, Integer limit) {
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeLimit = (limit == null || limit < 1) ? 20 : Math.min(limit, 100);
        int offset = (safePage - 1) * safeLimit;

        String countSql;
        String selectSql;
        Object[] countParams;
        Object[] selectParams;

        if (artistId != null) {
            countSql = "SELECT COUNT(*) FROM community_posts WHERE artist_id = ?";
            selectSql = """
                SELECT * FROM community_posts
                WHERE artist_id = ?
                ORDER BY is_pinned DESC, created_at DESC
                LIMIT ? OFFSET ?
                """;
            countParams = new Object[]{artistId};
            selectParams = new Object[]{artistId, safeLimit, offset};
        } else {
            countSql = "SELECT COUNT(*) FROM community_posts";
            selectSql = """
                SELECT * FROM community_posts
                ORDER BY is_pinned DESC, created_at DESC
                LIMIT ? OFFSET ?
                """;
            countParams = new Object[]{};
            selectParams = new Object[]{safeLimit, offset};
        }

        Integer total = jdbcTemplate.queryForObject(countSql, Integer.class, countParams);
        List<Map<String, Object>> posts = jdbcTemplate.queryForList(selectSql, selectParams);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", safePage);
        pagination.put("limit", safeLimit);
        pagination.put("total", total != null ? total : 0);
        pagination.put("totalPages", total != null ? (int) Math.ceil((double) total / safeLimit) : 0);

        Map<String, Object> result = new HashMap<>();
        result.put("posts", posts);
        result.put("pagination", pagination);
        return result;
    }

    @Transactional
    public Map<String, Object> detail(UUID id) {
        jdbcTemplate.update("UPDATE community_posts SET views = views + 1 WHERE id = ?", id);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM community_posts WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        return Map.of("post", rows.getFirst());
    }

    @Transactional
    public Map<String, Object> create(PostCreateRequest request, AuthUser user) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            INSERT INTO community_posts (artist_id, author_id, author_name, title, content)
            VALUES (?, CAST(? AS UUID), ?, ?, ?)
            RETURNING *
            """,
            request.artistId(),
            user.userId(),
            user.email().split("@")[0],
            request.title(),
            request.content()
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create post");
        }

        Map<String, Object> post = rows.getFirst();
        UUID postId = (UUID) post.get("id");

        try {
            ticketInternalClient.awardMembershipPoints(
                user.userId(), request.artistId(),
                "COMMUNITY_POST", POST_POINTS,
                "커뮤니티 글 작성", postId);
        } catch (Exception e) {
            log.warn("Failed to award points for post {}: {}", postId, e.getMessage());
        }

        return Map.of("post", post);
    }

    @Transactional
    public Map<String, Object> update(UUID id, PostUpdateRequest request, AuthUser user) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT author_id FROM community_posts WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        String ownerId = String.valueOf(rows.getFirst().get("author_id"));
        if (!ownerId.equals(user.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author can edit this post");
        }

        List<Map<String, Object>> updated = jdbcTemplate.queryForList("""
            UPDATE community_posts
            SET title = ?, content = ?, updated_at = NOW()
            WHERE id = ?
            RETURNING *
            """, request.title(), request.content(), id);

        return Map.of("post", updated.getFirst());
    }

    @Transactional
    public Map<String, Object> delete(UUID id, AuthUser user) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT author_id FROM community_posts WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        String ownerId = String.valueOf(rows.getFirst().get("author_id"));
        if (!ownerId.equals(user.userId()) && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission to delete this post");
        }

        jdbcTemplate.update("DELETE FROM community_posts WHERE id = ?", id);
        return Map.of("message", "Post deleted successfully");
    }
}
