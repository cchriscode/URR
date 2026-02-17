package guru.urr.statsservice.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsWriteService {

    private static final Logger log = LoggerFactory.getLogger(StatsWriteService.class);

    private final JdbcTemplate jdbcTemplate;

    public StatsWriteService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void recordReservationCreated(UUID eventId) {
        jdbcTemplate.update("""
            INSERT INTO daily_stats (date, total_reservations, confirmed_reservations, cancelled_reservations, total_revenue, payment_revenue, new_users, active_users, active_events)
            VALUES (CURRENT_DATE, 1, 0, 0, 0, 0, 0, 0, 0)
            ON CONFLICT (date) DO UPDATE SET total_reservations = daily_stats.total_reservations + 1
            """);

        if (eventId != null) {
            jdbcTemplate.update("""
                INSERT INTO event_stats (event_id, total_seats, reserved_seats, available_seats, total_reservations, confirmed_reservations, total_revenue, average_ticket_price)
                VALUES (?, 0, 0, 0, 1, 0, 0, 0)
                ON CONFLICT (event_id) DO UPDATE SET total_reservations = event_stats.total_reservations + 1
                """, eventId);
        }
    }

    @Transactional
    public void recordReservationConfirmed(UUID eventId, int amount) {
        jdbcTemplate.update("""
            INSERT INTO daily_stats (date, total_reservations, confirmed_reservations, cancelled_reservations, total_revenue, payment_revenue, new_users, active_users, active_events)
            VALUES (CURRENT_DATE, 0, 1, 0, ?, ?, 0, 0, 0)
            ON CONFLICT (date) DO UPDATE SET
                confirmed_reservations = daily_stats.confirmed_reservations + 1,
                total_revenue = daily_stats.total_revenue + ?,
                payment_revenue = daily_stats.payment_revenue + ?
            """, amount, amount, amount, amount);

        if (eventId != null) {
            jdbcTemplate.update("""
                INSERT INTO event_stats (event_id, total_seats, reserved_seats, available_seats, total_reservations, confirmed_reservations, total_revenue, average_ticket_price)
                VALUES (?, 0, 1, 0, 0, 1, ?, 0)
                ON CONFLICT (event_id) DO UPDATE SET
                    confirmed_reservations = event_stats.confirmed_reservations + 1,
                    reserved_seats = event_stats.reserved_seats + 1,
                    available_seats = GREATEST(event_stats.available_seats - 1, 0),
                    total_revenue = event_stats.total_revenue + ?
                """, eventId, amount, amount);
        }
    }

    @Transactional
    public void recordReservationCancelled(UUID eventId) {
        jdbcTemplate.update("""
            INSERT INTO daily_stats (date, total_reservations, confirmed_reservations, cancelled_reservations, total_revenue, payment_revenue, new_users, active_users, active_events)
            VALUES (CURRENT_DATE, 0, 0, 1, 0, 0, 0, 0, 0)
            ON CONFLICT (date) DO UPDATE SET cancelled_reservations = daily_stats.cancelled_reservations + 1
            """);
    }

    @Transactional
    public void recordPaymentRefunded(int amount) {
        jdbcTemplate.update("""
            INSERT INTO daily_stats (date, total_reservations, confirmed_reservations, cancelled_reservations, total_revenue, payment_revenue, new_users, active_users, active_events)
            VALUES (CURRENT_DATE, 0, 0, 1, 0, 0, 0, 0, 0)
            ON CONFLICT (date) DO UPDATE SET
                cancelled_reservations = daily_stats.cancelled_reservations + 1,
                total_revenue = daily_stats.total_revenue - ?,
                payment_revenue = daily_stats.payment_revenue - ?
            """, amount, amount);
    }

    @Transactional
    public void recordTransferCompleted(int totalPrice) {
        jdbcTemplate.update("""
            INSERT INTO daily_stats (date, total_reservations, confirmed_reservations, cancelled_reservations, total_revenue, payment_revenue, new_users, active_users, active_events)
            VALUES (CURRENT_DATE, 0, 0, 0, ?, ?, 0, 0, 0)
            ON CONFLICT (date) DO UPDATE SET
                total_revenue = daily_stats.total_revenue + ?,
                payment_revenue = daily_stats.payment_revenue + ?
            """, totalPrice, totalPrice, totalPrice, totalPrice);
    }

    @Transactional
    public void recordMembershipActivated() {
        jdbcTemplate.update("""
            INSERT INTO daily_stats (date, total_reservations, confirmed_reservations, cancelled_reservations, total_revenue, payment_revenue, new_users, active_users, active_events)
            VALUES (CURRENT_DATE, 0, 0, 0, 0, 0, 0, 1, 0)
            ON CONFLICT (date) DO UPDATE SET active_users = daily_stats.active_users + 1
            """);
    }
}
