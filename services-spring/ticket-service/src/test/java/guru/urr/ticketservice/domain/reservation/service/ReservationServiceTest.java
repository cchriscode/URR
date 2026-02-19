package guru.urr.ticketservice.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.ticketservice.domain.seat.service.SeatLockService;
import guru.urr.ticketservice.messaging.TicketEventProducer;
import guru.urr.ticketservice.shared.metrics.BusinessMetrics;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock private SeatLockService seatLockService;
    @Mock private TicketEventProducer ticketEventProducer;
    @Mock private BusinessMetrics metrics;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(jdbcTemplate, namedParameterJdbcTemplate, seatLockService, ticketEventProducer, metrics);
    }

    @Test
    void cancelReservation_success() {
        String userId = UUID.randomUUID().toString();
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Map<String, Object> reservation = new HashMap<>();
        reservation.put("id", reservationId);
        reservation.put("status", "pending");
        reservation.put("event_id", eventId);

        when(jdbcTemplate.queryForList(contains("SELECT id, status, event_id"), eq(reservationId), eq(userId)))
            .thenReturn(List.of(reservation));
        when(jdbcTemplate.queryForList(contains("SELECT ticket_type_id"), eq(reservationId)))
            .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(contains("UPDATE reservations SET status = 'cancelled'"), eq(reservationId)))
            .thenReturn(1);

        Map<String, Object> result = reservationService.cancelReservation(userId, reservationId);

        assertEquals("Reservation cancelled", result.get("message"));
    }

    @Test
    void cancelReservation_notFound_throws() {
        String userId = UUID.randomUUID().toString();
        UUID reservationId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(contains("SELECT id, status, event_id"), any(UUID.class), any(String.class)))
            .thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class,
            () -> reservationService.cancelReservation(userId, reservationId));
    }

    @Test
    void cancelReservation_alreadyCancelled_throws() {
        String userId = UUID.randomUUID().toString();
        UUID reservationId = UUID.randomUUID();

        Map<String, Object> reservation = new HashMap<>();
        reservation.put("id", reservationId);
        reservation.put("status", "cancelled");
        reservation.put("event_id", UUID.randomUUID());

        when(jdbcTemplate.queryForList(contains("SELECT id, status, event_id"), eq(reservationId), eq(userId)))
            .thenReturn(List.of(reservation));

        assertThrows(ResponseStatusException.class,
            () -> reservationService.cancelReservation(userId, reservationId));
    }

    @Test
    void validatePendingReservation_notFound_throws() {
        UUID reservationId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();

        when(jdbcTemplate.queryForList(contains("SELECT id, user_id, event_id"), eq(reservationId)))
            .thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class,
            () -> reservationService.validatePendingReservation(reservationId, userId));
    }
}
