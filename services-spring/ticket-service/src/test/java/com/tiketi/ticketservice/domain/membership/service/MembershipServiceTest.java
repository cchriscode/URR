package com.tiketi.ticketservice.domain.membership.service;

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
class MembershipServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    private MembershipService membershipService;

    @BeforeEach
    void setUp() {
        membershipService = new MembershipService(jdbcTemplate);
    }

    @Test
    void subscribe_success() {
        String userId = UUID.randomUUID().toString();
        UUID artistId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        Map<String, Object> artist = new HashMap<>();
        artist.put("id", artistId);
        artist.put("name", "Test Artist");
        artist.put("membership_price", 30000);

        Map<String, Object> insertedRow = new HashMap<>();
        insertedRow.put("id", membershipId);

        when(jdbcTemplate.queryForList(contains("SELECT id, name, membership_price"), eq(artistId)))
            .thenReturn(List.of(artist));
        when(jdbcTemplate.queryForList(contains("SELECT id, status, expires_at"), eq(userId), eq(artistId)))
            .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(contains("INSERT INTO artist_memberships"), eq(userId), eq(artistId)))
            .thenReturn(List.of(insertedRow));

        Map<String, Object> result = membershipService.subscribe(userId, artistId);

        assertEquals("pending", result.get("status"));
        assertEquals(30000, result.get("price"));
    }

    @Test
    void subscribe_alreadyActive_throws() {
        String userId = UUID.randomUUID().toString();
        UUID artistId = UUID.randomUUID();

        Map<String, Object> artist = new HashMap<>();
        artist.put("id", artistId);
        artist.put("name", "Test Artist");
        artist.put("membership_price", 30000);

        Map<String, Object> existing = new HashMap<>();
        existing.put("id", UUID.randomUUID());
        existing.put("status", "active");
        existing.put("expires_at", null);

        when(jdbcTemplate.queryForList(contains("SELECT id, name, membership_price"), eq(artistId)))
            .thenReturn(List.of(artist));
        when(jdbcTemplate.queryForList(contains("SELECT id, status, expires_at"), eq(userId), eq(artistId)))
            .thenReturn(List.of(existing));

        assertThrows(ResponseStatusException.class,
            () -> membershipService.subscribe(userId, artistId));
    }

    @Test
    void activateMembership_success() {
        UUID membershipId = UUID.randomUUID();

        Map<String, Object> row = new HashMap<>();
        row.put("id", membershipId);
        row.put("status", "pending");
        row.put("points", 0);

        when(jdbcTemplate.queryForList(contains("SELECT id, status, points"), eq(membershipId)))
            .thenReturn(List.of(row));
        when(jdbcTemplate.update(contains("UPDATE artist_memberships"), any(), eq(membershipId)))
            .thenReturn(1);
        when(jdbcTemplate.update(contains("INSERT INTO membership_point_logs"), any(), any(), anyInt(), any(), any()))
            .thenReturn(1);
        when(jdbcTemplate.update(contains("UPDATE artist_memberships SET points"), anyInt(), eq(membershipId)))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("SELECT points"), eq(Integer.class), eq(membershipId)))
            .thenReturn(200);
        when(jdbcTemplate.update(contains("UPDATE artist_memberships SET tier"), anyString(), eq(membershipId)))
            .thenReturn(1);

        assertDoesNotThrow(() -> membershipService.activateMembership(membershipId));
    }

    @Test
    void activateMembership_alreadyActive_noOp() {
        UUID membershipId = UUID.randomUUID();

        Map<String, Object> row = new HashMap<>();
        row.put("id", membershipId);
        row.put("status", "active");
        row.put("points", 500);

        when(jdbcTemplate.queryForList(contains("SELECT id, status, points"), eq(membershipId)))
            .thenReturn(List.of(row));

        assertDoesNotThrow(() -> membershipService.activateMembership(membershipId));
        verify(jdbcTemplate, never()).update(contains("UPDATE artist_memberships"), any(), eq(membershipId));
    }

    @Test
    void subscribe_artistNotFound_throws() {
        String userId = UUID.randomUUID().toString();
        UUID artistId = UUID.randomUUID();

        when(jdbcTemplate.queryForList(contains("SELECT id, name, membership_price"), eq(artistId)))
            .thenReturn(Collections.emptyList());

        assertThrows(ResponseStatusException.class,
            () -> membershipService.subscribe(userId, artistId));
    }
}
