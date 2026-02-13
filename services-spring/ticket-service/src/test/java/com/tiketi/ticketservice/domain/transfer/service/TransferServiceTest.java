package com.tiketi.ticketservice.domain.transfer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(jdbcTemplate);
    }

    @Test
    void createListing_notOwner_throws() {
        UUID reservationId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();

        Map<String, Object> res = new HashMap<>();
        res.put("user_id", UUID.randomUUID().toString());
        res.put("status", "confirmed");
        res.put("total_amount", 50000);
        res.put("artist_id", null);

        when(jdbcTemplate.queryForList(contains("SELECT r.id"), eq(reservationId)))
            .thenReturn(List.of(res));

        assertThrows(ResponseStatusException.class,
            () -> transferService.createListing(userId, reservationId));
    }

    @Test
    void createListing_notConfirmed_throws() {
        UUID reservationId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();

        Map<String, Object> res = new HashMap<>();
        res.put("user_id", userId);
        res.put("status", "pending");
        res.put("total_amount", 50000);
        res.put("artist_id", null);

        when(jdbcTemplate.queryForList(contains("SELECT r.id"), eq(reservationId)))
            .thenReturn(List.of(res));

        assertThrows(ResponseStatusException.class,
            () -> transferService.createListing(userId, reservationId));
    }

    @Test
    void completePurchase_success() {
        UUID transferId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        String buyerId = UUID.randomUUID().toString();

        Map<String, Object> transfer = new HashMap<>();
        transfer.put("id", transferId);
        transfer.put("reservation_id", reservationId);
        transfer.put("seller_id", UUID.randomUUID().toString());
        transfer.put("status", "listed");

        when(jdbcTemplate.queryForList(contains("SELECT id, reservation_id, seller_id"), eq(transferId)))
            .thenReturn(List.of(transfer));
        when(jdbcTemplate.update(contains("UPDATE reservations"), eq(buyerId), eq(reservationId)))
            .thenReturn(1);
        when(jdbcTemplate.update(contains("UPDATE ticket_transfers"), eq(buyerId), eq(transferId)))
            .thenReturn(1);

        assertDoesNotThrow(() -> transferService.completePurchase(transferId, buyerId, "card"));
    }

    @Test
    void completePurchase_notListed_throws() {
        UUID transferId = UUID.randomUUID();
        String buyerId = UUID.randomUUID().toString();

        Map<String, Object> transfer = new HashMap<>();
        transfer.put("id", transferId);
        transfer.put("reservation_id", UUID.randomUUID());
        transfer.put("seller_id", UUID.randomUUID().toString());
        transfer.put("status", "completed");

        when(jdbcTemplate.queryForList(contains("SELECT id, reservation_id, seller_id"), eq(transferId)))
            .thenReturn(List.of(transfer));

        assertThrows(ResponseStatusException.class,
            () -> transferService.completePurchase(transferId, buyerId, "card"));
    }

    @Test
    void validateForPurchase_notFound_throws() {
        UUID transferId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(contains("SELECT id, seller_id"), eq(transferId)))
            .thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class,
            () -> transferService.validateForPurchase(transferId, "user-id"));
    }
}
