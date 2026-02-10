package com.tiketi.ticketservice.service;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final JdbcTemplate jdbcTemplate;

    public TransferService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> createListing(String userId, UUID reservationId) {
        // 1. Validate reservation: must be confirmed and owned by user
        List<Map<String, Object>> resRows = jdbcTemplate.queryForList("""
            SELECT r.id, r.user_id, r.event_id, r.total_amount, r.status,
                   e.artist_id, e.title AS event_title
            FROM reservations r
            JOIN events e ON r.event_id = e.id
            WHERE r.id = ?
            """, reservationId);

        if (resRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }
        Map<String, Object> res = resRows.getFirst();

        if (!String.valueOf(res.get("user_id")).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your reservation");
        }
        if (!"confirmed".equals(String.valueOf(res.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only confirmed reservations can be transferred");
        }

        Object artistIdObj = res.get("artist_id");
        UUID artistId = artistIdObj != null ? (UUID) artistIdObj : null;

        // 2. Check no existing active listing for this reservation
        Integer existingCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ticket_transfers WHERE reservation_id = ? AND status = 'listed'",
            Integer.class, reservationId);
        if (existingCount != null && existingCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer already listed for this reservation");
        }

        // 3. Calculate fee — membership check only for artist events
        int feePercent = 10; // default for non-artist events
        if (artistId != null) {
            List<Map<String, Object>> memberRows = jdbcTemplate.queryForList(
                "SELECT id, tier, points, status FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND artist_id = ? AND status = 'active'",
                userId, artistId);
            if (memberRows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active membership required for this artist");
            }

            int points = ((Number) memberRows.getFirst().get("points")).intValue();
            String effectiveTier = computeEffectiveTier(points);

            if ("BRONZE".equals(effectiveTier)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bronze tier cannot transfer tickets");
            }
            feePercent = "SILVER".equals(effectiveTier) ? 10 : 5;
        }

        int originalPrice = ((Number) res.get("total_amount")).intValue();
        int transferFee = originalPrice * feePercent / 100;
        int totalPrice = originalPrice + transferFee;

        // 5. Insert transfer listing
        Map<String, Object> transfer = jdbcTemplate.queryForList("""
            INSERT INTO ticket_transfers (reservation_id, seller_id, artist_id, original_price, transfer_fee, transfer_fee_percent, total_price, status)
            VALUES (?, CAST(? AS UUID), ?, ?, ?, ?, ?, 'listed')
            RETURNING *
            """, reservationId, userId, artistId, originalPrice, transferFee, feePercent, totalPrice)
            .stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create transfer"));

        log.info("Transfer listing created: {} for reservation {} by user {}", transfer.get("id"), reservationId, userId);
        return Map.of("transfer", transfer, "message", "Transfer listed successfully");
    }

    public Map<String, Object> getAvailableTransfers(UUID artistId, int page, int limit) {
        int offset = (page - 1) * limit;

        String whereClause = "tt.status = 'listed'";
        Object[] params;
        if (artistId != null) {
            whereClause += " AND tt.artist_id = ?";
            params = new Object[]{artistId, limit, offset};
        } else {
            params = new Object[]{limit, offset};
        }

        String sql = """
            SELECT tt.*, e.title AS event_title, e.venue, e.event_date,
                   a.name AS artist_name, a.image_url AS artist_image_url,
                   (SELECT string_agg(s.seat_label, ', ' ORDER BY s.seat_label)
                    FROM reservation_items ri JOIN seats s ON ri.seat_id = s.id
                    WHERE ri.reservation_id = tt.reservation_id) AS seats
            FROM ticket_transfers tt
            JOIN reservations r ON tt.reservation_id = r.id
            JOIN events e ON r.event_id = e.id
            LEFT JOIN artists a ON tt.artist_id = a.id
            WHERE %s
            ORDER BY tt.created_at DESC
            LIMIT ? OFFSET ?
            """.formatted(whereClause);

        List<Map<String, Object>> transfers = jdbcTemplate.queryForList(sql, params);

        String countSql = "SELECT COUNT(*) FROM ticket_transfers tt WHERE " + whereClause.replace(" AND tt.artist_id = ?", artistId != null ? " AND tt.artist_id = '" + artistId + "'" : "");
        Integer total;
        if (artistId != null) {
            total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_transfers WHERE status = 'listed' AND artist_id = ?", Integer.class, artistId);
        } else {
            total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket_transfers WHERE status = 'listed'", Integer.class);
        }
        int safeTotal = total != null ? total : 0;

        return Map.of(
            "transfers", transfers,
            "pagination", Map.of("total", safeTotal, "page", page, "limit", limit, "totalPages", (int) Math.ceil((double) safeTotal / limit))
        );
    }

    public Map<String, Object> getMyListings(String userId) {
        List<Map<String, Object>> transfers = jdbcTemplate.queryForList("""
            SELECT tt.*, e.title AS event_title, e.venue, e.event_date,
                   a.name AS artist_name,
                   (SELECT string_agg(s.seat_label, ', ' ORDER BY s.seat_label)
                    FROM reservation_items ri JOIN seats s ON ri.seat_id = s.id
                    WHERE ri.reservation_id = tt.reservation_id) AS seats
            FROM ticket_transfers tt
            JOIN reservations r ON tt.reservation_id = r.id
            JOIN events e ON r.event_id = e.id
            LEFT JOIN artists a ON tt.artist_id = a.id
            WHERE tt.seller_id = CAST(? AS UUID)
            ORDER BY tt.created_at DESC
            """, userId);
        return Map.of("transfers", transfers);
    }

    public Map<String, Object> getTransferDetail(UUID transferId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT tt.*, e.title AS event_title, e.venue, e.event_date, e.poster_image_url,
                   a.name AS artist_name, a.image_url AS artist_image_url,
                   (SELECT string_agg(s.seat_label, ', ' ORDER BY s.seat_label)
                    FROM reservation_items ri JOIN seats s ON ri.seat_id = s.id
                    WHERE ri.reservation_id = tt.reservation_id) AS seats
            FROM ticket_transfers tt
            JOIN reservations r ON tt.reservation_id = r.id
            JOIN events e ON r.event_id = e.id
            LEFT JOIN artists a ON tt.artist_id = a.id
            WHERE tt.id = ?
            """, transferId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found");
        }
        return Map.of("transfer", rows.getFirst());
    }

    @Transactional
    public Map<String, Object> cancelListing(String userId, UUID transferId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, seller_id, status FROM ticket_transfers WHERE id = ?", transferId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found");
        }

        Map<String, Object> transfer = rows.getFirst();
        if (!String.valueOf(transfer.get("seller_id")).equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your transfer listing");
        }
        if (!"listed".equals(String.valueOf(transfer.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only listed transfers can be cancelled");
        }

        jdbcTemplate.update("UPDATE ticket_transfers SET status = 'cancelled', updated_at = NOW() WHERE id = ?", transferId);
        return Map.of("message", "Transfer cancelled");
    }

    public Map<String, Object> validateForPurchase(UUID transferId, String buyerId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, seller_id, artist_id, total_price, status FROM ticket_transfers WHERE id = ?", transferId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found");
        }

        Map<String, Object> transfer = rows.getFirst();
        if (!"listed".equals(String.valueOf(transfer.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer is no longer available");
        }
        if (String.valueOf(transfer.get("seller_id")).equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot buy your own transfer");
        }

        Object artistIdObj = transfer.get("artist_id");
        UUID artistId = artistIdObj != null ? (UUID) artistIdObj : null;

        // Buyer must have active membership for the same artist (only for artist events)
        if (artistId != null) {
            Integer memberCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM artist_memberships WHERE user_id = CAST(? AS UUID) AND artist_id = ? AND status = 'active'",
                Integer.class, buyerId, artistId);
            if (memberCount == null || memberCount == 0) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Buyer must have active membership for this artist");
            }
        }

        int totalPrice = ((Number) transfer.get("total_price")).intValue();

        Map<String, Object> result = new HashMap<>();
        result.put("transferId", transferId.toString());
        result.put("total_amount", totalPrice);
        result.put("totalAmount", totalPrice);
        result.put("artist_id", artistId != null ? artistId.toString() : null);
        return result;
    }

    @Transactional
    public void completePurchase(UUID transferId, String buyerId, String paymentMethod) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, reservation_id, seller_id, status FROM ticket_transfers WHERE id = ? FOR UPDATE", transferId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transfer not found");
        }

        Map<String, Object> transfer = rows.getFirst();
        if (!"listed".equals(String.valueOf(transfer.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer is no longer available");
        }

        UUID reservationId = (UUID) transfer.get("reservation_id");

        // Transfer reservation ownership
        jdbcTemplate.update(
            "UPDATE reservations SET user_id = CAST(? AS UUID), updated_at = NOW() WHERE id = ?",
            buyerId, reservationId);

        // Mark transfer complete
        jdbcTemplate.update("""
            UPDATE ticket_transfers
            SET status = 'completed', buyer_id = CAST(? AS UUID), completed_at = NOW(), updated_at = NOW()
            WHERE id = ?
            """, buyerId, transferId);

        log.info("Transfer {} completed: reservation {} transferred to buyer {}", transferId, reservationId, buyerId);
    }

    private String computeEffectiveTier(int points) {
        if (points >= 1500) return "DIAMOND";
        if (points >= 500) return "GOLD";
        return "SILVER";
    }
}
