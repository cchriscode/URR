package guru.urr.ticketservice.scheduling;

import guru.urr.ticketservice.domain.seat.service.SeatLockService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final SeatLockService seatLockService;

    public MaintenanceService(
        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
        SeatLockService seatLockService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.seatLockService = seatLockService;
    }

    @Scheduled(fixedDelayString = "${reservation.cleanup.interval-ms:30000}")
    @Transactional
    public void cleanupExpiredReservations() {
        List<Map<String, Object>> expired = jdbcTemplate.queryForList("""
            SELECT id, event_id
            FROM reservations
            WHERE payment_status = 'pending'
              AND status = 'pending'
              AND expires_at < NOW()
            FOR UPDATE SKIP LOCKED
            """);

        for (Map<String, Object> reservation : expired) {
            Object reservationId = reservation.get("id");
            UUID eventId = (UUID) reservation.get("event_id");
            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT seat_id, ticket_type_id, quantity FROM reservation_items WHERE reservation_id = ?", reservationId);

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
                    jdbcTemplate.update("UPDATE ticket_types SET available_quantity = available_quantity + ? WHERE id = ?", quantity, ticketTypeId);
                }
            }

            jdbcTemplate.update("UPDATE reservations SET status = 'expired', updated_at = NOW() WHERE id = ?", reservationId);
        }
    }

    @Scheduled(fixedDelayString = "${event.status.interval-ms:60000}")
    @Transactional
    public void updateEventStatuses() {
        jdbcTemplate.update("""
            UPDATE events
            SET status = 'on_sale', updated_at = NOW()
            WHERE status = 'upcoming' AND sale_start_date <= NOW() AND sale_end_date > NOW()
            """);

        jdbcTemplate.update("""
            UPDATE events
            SET status = 'ended', updated_at = NOW()
            WHERE status IN ('upcoming', 'on_sale')
              AND sale_end_date <= NOW()
              AND status != 'cancelled'
            """);

        jdbcTemplate.update("""
            UPDATE events
            SET status = 'ended', updated_at = NOW()
            WHERE status NOT IN ('ended', 'cancelled')
              AND event_date < NOW()
            """);
    }

    @Transactional
    public void forceStatusReschedule() {
        updateEventStatuses();
    }
}
