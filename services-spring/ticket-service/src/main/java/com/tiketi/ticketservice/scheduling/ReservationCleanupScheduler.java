package com.tiketi.ticketservice.scheduling;

import com.tiketi.ticketservice.domain.seat.service.SeatLockService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationCleanupScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final SeatLockService seatLockService;

    public ReservationCleanupScheduler(JdbcTemplate jdbcTemplate, SeatLockService seatLockService) {
        this.jdbcTemplate = jdbcTemplate;
        this.seatLockService = seatLockService;
    }

    /**
     * H6: Finds expired pending reservations and cleans them up.
     * Sets status to 'expired', releases Redis seat locks, and marks seats as available.
     */
    @Scheduled(fixedRateString = "${reservation.cleanup.interval-ms:30000}")
    @Transactional
    public void cleanupExpiredReservations() {
        try {
            List<Map<String, Object>> expired = jdbcTemplate.queryForList("""
                SELECT id, event_id
                FROM reservations
                WHERE status = 'pending'
                  AND expires_at < NOW()
                FOR UPDATE SKIP LOCKED
                """);

            if (expired.isEmpty()) {
                return;
            }

            log.info("Reservation cleanup: found {} expired pending reservations", expired.size());

            for (Map<String, Object> reservation : expired) {
                UUID reservationId = (UUID) reservation.get("id");
                UUID eventId = (UUID) reservation.get("event_id");

                try {
                    List<Map<String, Object>> items = jdbcTemplate.queryForList(
                        "SELECT seat_id, ticket_type_id, quantity FROM reservation_items WHERE reservation_id = ?",
                        reservationId);

                    for (Map<String, Object> item : items) {
                        Object seatId = item.get("seat_id");
                        Object ticketTypeId = item.get("ticket_type_id");
                        Number quantity = (Number) item.get("quantity");

                        if (seatId != null) {
                            jdbcTemplate.update("""
                                UPDATE seats SET status = 'available', version = version + 1,
                                fencing_token = 0, locked_by = NULL, updated_at = NOW()
                                WHERE id = ?
                                """, seatId);

                            try {
                                seatLockService.cleanupLock(eventId, (UUID) seatId);
                            } catch (Exception ex) {
                                log.warn("Failed to cleanup Redis seat lock for seat {}: {}", seatId, ex.getMessage());
                            }
                        }
                        if (ticketTypeId != null) {
                            jdbcTemplate.update(
                                "UPDATE ticket_types SET available_quantity = available_quantity + ? WHERE id = ?",
                                quantity, ticketTypeId);
                        }
                    }

                    jdbcTemplate.update(
                        "UPDATE reservations SET status = 'expired', updated_at = NOW() WHERE id = ?",
                        reservationId);

                    log.info("Reservation cleanup: expired reservation {} with {} items", reservationId, items.size());
                } catch (Exception e) {
                    log.error("Reservation cleanup: failed to expire reservation {}: {}", reservationId, e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Reservation cleanup scheduler failed: {}", e.getMessage(), e);
        }
    }
}
