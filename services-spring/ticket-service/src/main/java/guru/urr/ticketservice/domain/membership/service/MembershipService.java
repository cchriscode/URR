package guru.urr.ticketservice.domain.membership.service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class MembershipService {

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);

    public static final int GOLD_THRESHOLD = 500;
    public static final int DIAMOND_THRESHOLD = 1500;
    private static final int JOIN_BONUS_POINTS = 200;

    private final JdbcTemplate jdbcTemplate;

    public MembershipService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> subscribe(String userId, UUID artistId) {
        List<Map<String, Object>> artistRows = jdbcTemplate.queryForList(
            "SELECT id, name, membership_price FROM artists WHERE id = ?", artistId);
        if (artistRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found");
        }

        String artistName = String.valueOf(artistRows.getFirst().get("name"));
        int price = ((Number) artistRows.getFirst().get("membership_price")).intValue();

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
            "SELECT id, status, expires_at FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND artist_id = ?",
            userId, artistId);

        if (!existing.isEmpty()) {
            String status = String.valueOf(existing.getFirst().get("status"));
            if ("active".equals(status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Already an active member of this artist");
            }
            if ("pending".equals(status)) {
                UUID membershipId = (UUID) existing.getFirst().get("id");
                return Map.of("membershipId", membershipId.toString(), "artistId", artistId.toString(),
                    "artistName", artistName, "price", price, "status", "pending");
            }
            UUID membershipId = (UUID) existing.getFirst().get("id");
            jdbcTemplate.update(
                "UPDATE artist_memberships SET status = 'pending', updated_at = NOW() WHERE id = ?", membershipId);
            return Map.of("membershipId", membershipId.toString(), "artistId", artistId.toString(),
                "artistName", artistName, "price", price, "status", "pending");
        }

        Map<String, Object> row = jdbcTemplate.queryForList("""
            INSERT INTO artist_memberships (user_id, artist_id, tier, points, status, expires_at)
            VALUES (CAST(? AS UUID), ?, 'SILVER', 0, 'pending', NOW())
            RETURNING *
            """, userId, artistId)
            .stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to create membership"));

        UUID membershipId = (UUID) row.get("id");
        return Map.of("membershipId", membershipId.toString(), "artistId", artistId.toString(),
            "artistName", artistName, "price", price, "status", "pending");
    }

    @Transactional
    public void activateMembership(UUID membershipId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, status, points FROM artist_memberships WHERE id = ?", membershipId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        String status = String.valueOf(rows.getFirst().get("status"));
        if ("active".equals(status)) {
            return;
        }

        OffsetDateTime expiresAt = OffsetDateTime.now().plusYears(1);
        jdbcTemplate.update("""
            UPDATE artist_memberships
            SET status = 'active', expires_at = ?, joined_at = NOW(), updated_at = NOW()
            WHERE id = ?
            """, Timestamp.from(expiresAt.toInstant()), membershipId);

        int existingPoints = ((Number) rows.getFirst().get("points")).intValue();
        String actionType = existingPoints > 0 ? "MEMBERSHIP_RENEW" : "MEMBERSHIP_JOIN";
        String desc = existingPoints > 0 ? "Membership renewed" : "Welcome bonus for membership join";
        addPoints(membershipId, actionType, JOIN_BONUS_POINTS, desc, null);
    }

    public Map<String, Object> validatePendingMembership(UUID membershipId, String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT am.id, am.user_id, am.artist_id, am.status, a.membership_price, a.name AS artist_name
            FROM artist_memberships am JOIN artists a ON am.artist_id = a.id
            WHERE am.id = ?
            """, membershipId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        Map<String, Object> m = rows.getFirst();
        if (!String.valueOf(m.get("user_id")).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your membership");
        }
        if (!"pending".equals(String.valueOf(m.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Membership is not pending payment");
        }
        int price = ((Number) m.get("membership_price")).intValue();
        Map<String, Object> result = new HashMap<>();
        result.put("membershipId", membershipId.toString());
        result.put("total_amount", price);
        result.put("totalAmount", price);
        result.put("artist_name", String.valueOf(m.get("artist_name")));
        return result;
    }

    public Map<String, Object> getMyMemberships(String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT am.id, am.user_id, am.artist_id, am.tier, am.points, am.status,
                   am.joined_at, am.expires_at,
                   a.name AS artist_name, a.image_url AS artist_image_url,
                   a.membership_price
            FROM artist_memberships am
            JOIN artists a ON am.artist_id = a.id
            WHERE am.user_id = CAST(? AS UUID)
            ORDER BY am.joined_at DESC
            """, userId);

        List<Map<String, Object>> memberships = rows.stream().map(row -> {
            Map<String, Object> m = new HashMap<>(row);
            int points = ((Number) row.get("points")).intValue();
            m.put("effective_tier", computeEffectiveTier(points));
            return m;
        }).toList();

        return Map.of("memberships", memberships);
    }

    public Map<String, Object> getMyMembershipForArtist(String userId, UUID artistId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT am.*, a.name AS artist_name, a.image_url AS artist_image_url, a.membership_price
            FROM artist_memberships am
            JOIN artists a ON am.artist_id = a.id
            WHERE am.user_id = CAST(? AS UUID) AND am.artist_id = ?
            """, userId, artistId);

        if (rows.isEmpty()) {
            return Map.of("membership", Map.of(), "pointHistory", List.of(), "tier", "BRONZE");
        }

        Map<String, Object> membership = new HashMap<>(rows.getFirst());
        int points = ((Number) membership.get("points")).intValue();
        membership.put("effective_tier", computeEffectiveTier(points));

        UUID membershipId = (UUID) membership.get("id");
        List<Map<String, Object>> pointHistory = jdbcTemplate.queryForList("""
            SELECT id, action_type, points, description, reference_id, created_at
            FROM membership_point_logs
            WHERE membership_id = ?
            ORDER BY created_at DESC
            LIMIT 50
            """, membershipId);

        Map<String, Object> benefits = getBenefitsForTier(computeEffectiveTier(points));

        Map<String, Object> result = new HashMap<>();
        result.put("membership", membership);
        result.put("pointHistory", pointHistory);
        result.put("benefits", benefits);
        return result;
    }

    public Map<String, Object> getUserBenefitsForArtist(String userId, UUID artistId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT tier, points, status FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND artist_id = ?",
            userId, artistId);

        String effectiveTier;
        if (rows.isEmpty() || !"active".equals(String.valueOf(rows.getFirst().get("status")))) {
            effectiveTier = "BRONZE";
        } else {
            int points = ((Number) rows.getFirst().get("points")).intValue();
            effectiveTier = computeEffectiveTier(points);
        }

        Map<String, Object> benefits = getBenefitsForTier(effectiveTier);
        benefits.put("tier", effectiveTier);
        return benefits;
    }

    @Transactional
    public void addPoints(UUID membershipId, String actionType, int points, String description, UUID referenceId) {
        jdbcTemplate.update("""
            INSERT INTO membership_point_logs (membership_id, action_type, points, description, reference_id)
            VALUES (?, ?, ?, ?, ?)
            """, membershipId, actionType, points, description, referenceId);

        jdbcTemplate.update(
            "UPDATE artist_memberships SET points = points + ?, updated_at = NOW() WHERE id = ?",
            points, membershipId);

        Integer totalPoints = jdbcTemplate.queryForObject(
            "SELECT points FROM artist_memberships WHERE id = ?", Integer.class, membershipId);
        if (totalPoints == null) return;

        String newTier = computeEffectiveTier(totalPoints);
        jdbcTemplate.update(
            "UPDATE artist_memberships SET tier = ?, updated_at = NOW() WHERE id = ?",
            newTier, membershipId);

        log.debug("Membership {} now has {} points, tier={}", membershipId, totalPoints, newTier);
    }

    public void awardPointsForArtist(String userId, UUID artistId, String actionType, int points, String description, UUID referenceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND artist_id = ? AND status = 'active'",
            userId, artistId);
        if (rows.isEmpty()) {
            log.debug("No active membership for user {} artist {}, skipping points", userId, artistId);
            return;
        }
        UUID membershipId = (UUID) rows.getFirst().get("id");
        addPoints(membershipId, actionType, points, description, referenceId);
    }

    public void awardPointsToAllMemberships(String userId, String actionType, int points, String description, UUID referenceId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND status = 'active'", userId);
        for (Map<String, Object> row : rows) {
            UUID membershipId = (UUID) row.get("id");
            addPoints(membershipId, actionType, points, description, referenceId);
        }
    }

    private String computeEffectiveTier(int points) {
        if (points >= DIAMOND_THRESHOLD) return "DIAMOND";
        if (points >= GOLD_THRESHOLD) return "GOLD";
        return "SILVER";
    }

    private Map<String, Object> getBenefitsForTier(String tier) {
        Map<String, Object> benefits = new LinkedHashMap<>();
        switch (tier) {
            case "DIAMOND" -> {
                benefits.put("preSalePhase", 1);
                benefits.put("preSaleLabel", "선예매 1");
                benefits.put("bookingFeeSurcharge", 1000);
                benefits.put("transferAccess", true);
                benefits.put("transferFeePercent", 5);
            }
            case "GOLD" -> {
                benefits.put("preSalePhase", 2);
                benefits.put("preSaleLabel", "선예매 2");
                benefits.put("bookingFeeSurcharge", 2000);
                benefits.put("transferAccess", true);
                benefits.put("transferFeePercent", 5);
            }
            case "SILVER" -> {
                benefits.put("preSalePhase", 3);
                benefits.put("preSaleLabel", "선예매 3");
                benefits.put("bookingFeeSurcharge", 3000);
                benefits.put("transferAccess", true);
                benefits.put("transferFeePercent", 10);
            }
            default -> {
                benefits.put("preSalePhase", null);
                benefits.put("preSaleLabel", "일반예매");
                benefits.put("bookingFeeSurcharge", 0);
                benefits.put("transferAccess", false);
                benefits.put("transferFeePercent", null);
            }
        }
        benefits.put("nextTierThreshold", switch (tier) {
            case "SILVER" -> GOLD_THRESHOLD;
            case "GOLD" -> DIAMOND_THRESHOLD;
            default -> null;
        });
        return benefits;
    }
}
