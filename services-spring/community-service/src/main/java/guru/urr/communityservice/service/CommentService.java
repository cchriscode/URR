package guru.urr.communityservice.service;

import guru.urr.communityservice.dto.CommentCreateRequest;
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
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);
    private static final int COMMENT_POINTS = 10;

    private final JdbcTemplate jdbcTemplate;
    private final TicketInternalClient ticketInternalClient;

    public CommentService(JdbcTemplate jdbcTemplate, TicketInternalClient ticketInternalClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listByPost(UUID postId, Integer page, Integer limit) {
        int safePage = (page == null || page < 1) ? 1 : page;
        int safeLimit = (limit == null || limit < 1) ? 20 : Math.min(limit, 100);
        int offset = (safePage - 1) * safeLimit;

        Integer total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM community_comments WHERE post_id = ?", Integer.class, postId);

        List<Map<String, Object>> comments = jdbcTemplate.queryForList("""
            SELECT * FROM community_comments
            WHERE post_id = ?
            ORDER BY created_at ASC
            LIMIT ? OFFSET ?
            """, postId, safeLimit, offset);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", safePage);
        pagination.put("limit", safeLimit);
        pagination.put("total", total != null ? total : 0);

        Map<String, Object> result = new HashMap<>();
        result.put("comments", comments);
        result.put("pagination", pagination);
        return result;
    }

    @Transactional
    public Map<String, Object> create(UUID postId, CommentCreateRequest request, AuthUser user) {
        // Verify post exists and get artist_id for points
        List<Map<String, Object>> postRows = jdbcTemplate.queryForList(
            "SELECT id, artist_id FROM community_posts WHERE id = ?", postId);
        if (postRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }

        UUID artistId = (UUID) postRows.getFirst().get("artist_id");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            INSERT INTO community_comments (post_id, author_id, author_name, content)
            VALUES (?, CAST(? AS UUID), ?, ?)
            RETURNING *
            """,
            postId,
            user.userId(),
            user.email().split("@")[0],
            request.content()
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create comment");
        }

        // Increment comment count
        jdbcTemplate.update(
            "UPDATE community_posts SET comment_count = comment_count + 1 WHERE id = ?", postId);

        Map<String, Object> comment = rows.getFirst();
        UUID commentId = (UUID) comment.get("id");

        try {
            ticketInternalClient.awardMembershipPoints(
                user.userId(), artistId,
                "COMMUNITY_COMMENT", COMMENT_POINTS,
                "커뮤니티 댓글 작성", commentId);
        } catch (Exception e) {
            log.warn("Failed to award points for comment {}: {}", commentId, e.getMessage());
        }

        return Map.of("comment", comment);
    }

    @Transactional
    public Map<String, Object> delete(UUID commentId, AuthUser user) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT author_id, post_id FROM community_comments WHERE id = ?", commentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }

        String ownerId = String.valueOf(rows.getFirst().get("author_id"));
        if (!ownerId.equals(user.userId()) && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No permission to delete this comment");
        }

        UUID postId = (UUID) rows.getFirst().get("post_id");
        jdbcTemplate.update("DELETE FROM community_comments WHERE id = ?", commentId);
        jdbcTemplate.update(
            "UPDATE community_posts SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = ?", postId);

        return Map.of("message", "Comment deleted successfully");
    }
}
