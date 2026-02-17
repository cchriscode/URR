package guru.urr.ticketservice.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.ticketservice.domain.membership.service.MembershipService;
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
    @Mock private MembershipService membershipService;
    @Mock private SeatLockService seatLockService;
    @Mock private TicketEventProducer ticketEventProducer;
    @Mock private BusinessMetrics metrics;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(jdbcTemplate, namedParameterJdbcTemplate, membershipService, seatLockService, ticketEventProducer, metrics);
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
    void confirmReservationPayment_success() {
        UUID reservationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();

        Map<String, Object> resRow = new HashMap<>();
        resRow.put("user_id", userId);
        resRow.put("event_id", eventId);
        resRow.put("artist_id", null);

        when(jdbcTemplate.queryForList(contains("SELECT r.user_id, r.event_id"), eq(reservationId)))
            .thenReturn(List.of(resRow));
        when(jdbcTemplate.queryForList(contains("SELECT ri.seat_id"), eq(reservationId)))
            .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(contains("UPDATE reservations"), eq("card"), eq(reservationId)))
            .thenReturn(1);
        when(jdbcTemplate.queryForList(contains("SELECT seat_id FROM reservation_items"), eq(reservationId)))
            .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> reservationService.confirmReservationPayment(reservationId, "card"));
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

    @Test
    void markReservationRefunded_updatesStatus() {
        UUID reservationId = UUID.randomUUID();

        when(jdbcTemplate.update(contains("UPDATE reservations"), eq(reservationId))).thenReturn(1);
        when(jdbcTemplate.update(contains("UPDATE seats"), eq(reservationId))).thenReturn(0);

        assertDoesNotThrow(() -> reservationService.markReservationRefunded(reservationId));
        verify(jdbcTemplate).update(contains("status = 'cancelled'"), eq(reservationId));
    }
}
