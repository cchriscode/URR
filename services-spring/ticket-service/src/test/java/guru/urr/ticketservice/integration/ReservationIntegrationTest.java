package guru.urr.ticketservice.integration;

import static org.junit.jupiter.api.Assertions.*;

import guru.urr.ticketservice.domain.reservation.service.ReservationService;
import guru.urr.ticketservice.domain.seat.service.SeatLockService;
import guru.urr.ticketservice.messaging.TicketEventProducer;
import guru.urr.ticketservice.shared.client.PaymentInternalClient;
import guru.urr.ticketservice.shared.metrics.BusinessMetrics;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class ReservationIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ReservationService reservationService;

    @MockBean private TicketEventProducer ticketEventProducer;
    @MockBean private SeatLockService seatLockService;
    @MockBean private PaymentInternalClient paymentInternalClient;
    @MockBean private BusinessMetrics businessMetrics;
    @MockBean private StringRedisTemplate stringRedisTemplate;

    private UUID eventId;
    private UUID ticketTypeId;

    @BeforeEach
    void setUp() {
        // Clean up
        jdbcTemplate.update("DELETE FROM reservation_items");
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update("DELETE FROM seats");
        jdbcTemplate.update("DELETE FROM ticket_types");
        jdbcTemplate.update("DELETE FROM events");

        // Seed test data
        eventId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();

        jdbcTemplate.update("""
            INSERT INTO events (id, title, venue, event_date, status, sale_start_date, sale_end_date)
            VALUES (?, 'Test Concert', 'Test Venue', CURRENT_TIMESTAMP + INTERVAL '30' DAY,
                    'on_sale', CURRENT_TIMESTAMP - INTERVAL '1' DAY, CURRENT_TIMESTAMP + INTERVAL '14' DAY)
            """, eventId);

        jdbcTemplate.update("""
            INSERT INTO ticket_types (id, event_id, name, price, total_quantity, available_quantity)
            VALUES (?, ?, 'General', 50000, 100, 100)
            """, ticketTypeId, eventId);
    }

    @Test
    void cancelReservation_withRealDb() {
        UUID userId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String reservationNumber = "RES-" + System.currentTimeMillis();

        // Insert reservation directly
        jdbcTemplate.update("""
            INSERT INTO reservations (id, user_id, event_id, reservation_number, total_amount, status, expires_at)
            VALUES (?, ?, ?, ?, 50000, 'pending', CURRENT_TIMESTAMP + INTERVAL '15' MINUTE)
            """, reservationId, userId, eventId, reservationNumber);

        // Cancel reservation
        Map<String, Object> result = reservationService.cancelReservation(userId.toString(), reservationId);

        assertEquals("Reservation cancelled", result.get("message"));

        // Verify in DB
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status FROM reservations WHERE id = ?", reservationId);
        assertEquals("cancelled", rows.getFirst().get("status"));
    }

    @Test
    void cancelReservation_notFound_throws() {
        UUID userId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();

        assertThrows(ResponseStatusException.class,
                () -> reservationService.cancelReservation(userId.toString(), nonExistentId));
    }

    @Test
    void cancelReservation_alreadyCancelled_throws() {
        UUID userId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String reservationNumber = "RES-CANCELLED-" + System.currentTimeMillis();

        // Insert already cancelled reservation
        jdbcTemplate.update("""
            INSERT INTO reservations (id, user_id, event_id, reservation_number, total_amount, status)
            VALUES (?, ?, ?, ?, 50000, 'cancelled')
            """, reservationId, userId, eventId, reservationNumber);

        assertThrows(ResponseStatusException.class,
                () -> reservationService.cancelReservation(userId.toString(), reservationId));
    }

    @Test
    void myReservations_returnsUserReservations() {
        UUID userId = UUID.randomUUID();

        // Insert two reservations for this user
        jdbcTemplate.update("""
            INSERT INTO reservations (id, user_id, event_id, reservation_number, total_amount, status)
            VALUES (?, ?, ?, ?, 50000, 'pending')
            """, UUID.randomUUID(), userId, eventId, "RES-A-" + System.currentTimeMillis());

        jdbcTemplate.update("""
            INSERT INTO reservations (id, user_id, event_id, reservation_number, total_amount, status)
            VALUES (?, ?, ?, ?, 80000, 'confirmed')
            """, UUID.randomUUID(), userId, eventId, "RES-B-" + System.currentTimeMillis());

        Map<String, Object> result = reservationService.myReservations(userId.toString(), 1, 20);

        assertNotNull(result.get("reservations"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reservations = (List<Map<String, Object>>) result.get("reservations");
        assertEquals(2, reservations.size());
    }
}
