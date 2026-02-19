package guru.urr.ticketservice.domain.reservation.service;

import guru.urr.ticketservice.domain.membership.service.MembershipService;
import guru.urr.ticketservice.domain.seat.service.SeatLockService;
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
public class ReservationPaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(ReservationPaymentHandler.class);
    private static final int TICKET_PURCHASE_POINTS = 100;

    private final JdbcTemplate jdbcTemplate;
    private final SeatLockService seatLockService;
    private final MembershipService membershipService;

    public ReservationPaymentHandler(JdbcTemplate jdbcTemplate, SeatLockService seatLockService,
                                      MembershipService membershipService) {
        this.jdbcTemplate = jdbcTemplate;
        this.seatLockService = seatLockService;
        this.membershipService = membershipService;
    }

    @Transactional
    public void confirmReservationPayment(UUID reservationId, String paymentMethod) {
        // Get reservation + event info for Redis lock verification
        List<Map<String, Object>> resRows = jdbcTemplate.queryForList(
            "SELECT r.user_id, r.event_id, e.artist_id FROM reservations r JOIN events e ON r.event_id = e.id WHERE r.id = ?",
            reservationId);

        if (!resRows.isEmpty()) {
            String userId = String.valueOf(resRows.getFirst().get("user_id"));
            UUID eventId = (UUID) resRows.getFirst().get("event_id");

            // Verify fencing tokens via Redis for each seat
            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT ri.seat_id, s.fencing_token FROM reservation_items ri LEFT JOIN seats s ON ri.seat_id = s.id WHERE ri.reservation_id = ?",
                reservationId);

            for (Map<String, Object> item : items) {
                Object seatIdObj = item.get("seat_id");
                Object tokenObj = item.get("fencing_token");
                if (seatIdObj != null && tokenObj != null) {
                    UUID seatId = (UUID) seatIdObj;
                    long token = ((Number) tokenObj).longValue();
                    if (token > 0 && !seatLockService.verifyForPayment(eventId, seatId, userId, token)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat lock expired or stolen. Please try again.");
                    }
                }
            }
        }

        int updated = jdbcTemplate.update("""
            UPDATE reservations
            SET status = 'confirmed',
                payment_status = 'completed',
                payment_method = ?,
                updated_at = NOW()
            WHERE id = ?
              AND status = 'pending'
            """, paymentMethod, reservationId);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation cannot be confirmed");
        }

        // Update seats to reserved and clean up Redis locks
        List<Map<String, Object>> seatItems = jdbcTemplate.queryForList(
            "SELECT seat_id FROM reservation_items WHERE reservation_id = ? AND seat_id IS NOT NULL", reservationId);

        UUID eventId = resRows.isEmpty() ? null : (UUID) resRows.getFirst().get("event_id");

        for (Map<String, Object> item : seatItems) {
            UUID seatId = (UUID) item.get("seat_id");
            jdbcTemplate.update("UPDATE seats SET status = 'reserved', updated_at = NOW() WHERE id = ?", seatId);
            // Clean up Redis seat lock
            if (eventId != null) {
                seatLockService.cleanupLock(eventId, seatId);
            }
        }

        // Award membership points for ticket purchase
        try {
            if (!resRows.isEmpty() && resRows.getFirst().get("artist_id") != null) {
                UUID artistId = (UUID) resRows.getFirst().get("artist_id");
                String userId = String.valueOf(resRows.getFirst().get("user_id"));
                membershipService.awardPointsForArtist(userId, artistId, "TICKET_PURCHASE", TICKET_PURCHASE_POINTS,
                    "Points for ticket purchase", reservationId);
            }
        } catch (Exception e) {
            log.warn("Failed to award membership points for reservation {}: {}", reservationId, e.getMessage());
        }
    }

    @Transactional
    public void markReservationRefunded(UUID reservationId) {
        jdbcTemplate.update("""
            UPDATE reservations
            SET status = 'cancelled',
                payment_status = 'refunded',
                updated_at = NOW()
            WHERE id = ?
            """, reservationId);

        jdbcTemplate.update("""
            UPDATE seats
            SET status = 'available', updated_at = NOW()
            WHERE id IN (
                SELECT seat_id FROM reservation_items
                WHERE reservation_id = ? AND seat_id IS NOT NULL
            )
            """, reservationId);
    }
}
