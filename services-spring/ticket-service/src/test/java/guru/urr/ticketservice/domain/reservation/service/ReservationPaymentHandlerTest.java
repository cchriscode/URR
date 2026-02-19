package guru.urr.ticketservice.domain.reservation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.ticketservice.domain.membership.service.MembershipService;
import guru.urr.ticketservice.domain.seat.service.SeatLockService;
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

@ExtendWith(MockitoExtension.class)
class ReservationPaymentHandlerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private SeatLockService seatLockService;
    @Mock private MembershipService membershipService;

    private ReservationPaymentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReservationPaymentHandler(jdbcTemplate, seatLockService, membershipService);
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

        assertDoesNotThrow(() -> handler.confirmReservationPayment(reservationId, "card"));
    }

    @Test
    void markReservationRefunded_updatesStatus() {
        UUID reservationId = UUID.randomUUID();

        when(jdbcTemplate.update(contains("UPDATE reservations"), eq(reservationId))).thenReturn(1);
        when(jdbcTemplate.update(contains("UPDATE seats"), eq(reservationId))).thenReturn(0);

        assertDoesNotThrow(() -> handler.markReservationRefunded(reservationId));
        verify(jdbcTemplate).update(contains("status = 'cancelled'"), eq(reservationId));
    }
}
