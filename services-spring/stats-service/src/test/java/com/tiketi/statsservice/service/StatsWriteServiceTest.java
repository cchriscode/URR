package com.tiketi.statsservice.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class StatsWriteServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private StatsWriteService statsWriteService;

    @BeforeEach
    void setUp() {
        statsWriteService = new StatsWriteService(jdbcTemplate);
    }

    @Test
    void recordReservationCreated_updatesDailyAndEventStats() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString())).thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(UUID.class))).thenReturn(1);

        statsWriteService.recordReservationCreated(eventId);

        verify(jdbcTemplate).update(contains("INSERT INTO daily_stats"));
        verify(jdbcTemplate).update(contains("INSERT INTO event_stats"), eq(eventId));
    }

    @Test
    void recordReservationCreated_nullEventId_onlyUpdatesDaily() {
        when(jdbcTemplate.update(anyString())).thenReturn(1);

        statsWriteService.recordReservationCreated(null);

        verify(jdbcTemplate, times(1)).update(anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(UUID.class));
    }

    @Test
    void recordReservationConfirmed_updatesRevenueAndCount() {
        UUID eventId = UUID.randomUUID();
        int amount = 50000;

        when(jdbcTemplate.update(anyString(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(UUID.class), anyInt(), anyInt())).thenReturn(1);

        statsWriteService.recordReservationConfirmed(eventId, amount);

        verify(jdbcTemplate).update(contains("confirmed_reservations"), eq(amount), eq(amount), eq(amount), eq(amount));
        verify(jdbcTemplate).update(contains("event_stats"), eq(eventId), eq(amount), eq(amount));
    }

    @Test
    void recordReservationCancelled_incrementsCancelCount() {
        UUID eventId = UUID.randomUUID();
        when(jdbcTemplate.update(anyString())).thenReturn(1);

        statsWriteService.recordReservationCancelled(eventId);

        verify(jdbcTemplate).update(contains("cancelled_reservations"));
    }

    @Test
    void recordPaymentRefunded_decrementsRevenue() {
        int amount = 30000;
        when(jdbcTemplate.update(anyString(), anyInt(), anyInt())).thenReturn(1);

        statsWriteService.recordPaymentRefunded(amount);

        verify(jdbcTemplate).update(contains("total_revenue = daily_stats.total_revenue -"), eq(amount), eq(amount));
    }

    @Test
    void recordTransferCompleted_addsRevenue() {
        int totalPrice = 110000;
        when(jdbcTemplate.update(anyString(), anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(1);

        statsWriteService.recordTransferCompleted(totalPrice);

        verify(jdbcTemplate).update(contains("total_revenue = daily_stats.total_revenue +"),
            eq(totalPrice), eq(totalPrice), eq(totalPrice), eq(totalPrice));
    }

    @Test
    void recordMembershipActivated_incrementsActiveUsers() {
        when(jdbcTemplate.update(anyString())).thenReturn(1);

        statsWriteService.recordMembershipActivated();

        verify(jdbcTemplate).update(contains("active_users = daily_stats.active_users + 1"));
    }
}
