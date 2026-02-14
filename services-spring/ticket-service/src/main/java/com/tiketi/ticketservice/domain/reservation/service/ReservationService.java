package com.tiketi.ticketservice.domain.reservation.service;

import com.tiketi.ticketservice.domain.reservation.dto.CreateReservationRequest;
import com.tiketi.ticketservice.domain.reservation.dto.ReservationItemRequest;
import com.tiketi.ticketservice.domain.reservation.dto.SeatReserveRequest;
import com.tiketi.ticketservice.domain.membership.service.MembershipService;
import com.tiketi.ticketservice.domain.seat.service.SeatLockService;
import com.tiketi.ticketservice.shared.client.PaymentInternalClient;
import com.tiketi.ticketservice.messaging.TicketEventProducer;
import com.tiketi.ticketservice.messaging.event.ReservationCancelledEvent;
import com.tiketi.ticketservice.shared.metrics.BusinessMetrics;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final int MAX_SEATS_PER_RESERVATION = 1;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final MembershipService membershipService;
    private final SeatLockService seatLockService;
    private final TicketEventProducer ticketEventProducer;
    private final BusinessMetrics metrics;

    public ReservationService(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                              MembershipService membershipService, SeatLockService seatLockService,
                              TicketEventProducer ticketEventProducer, BusinessMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.membershipService = membershipService;
        this.seatLockService = seatLockService;
        this.ticketEventProducer = ticketEventProducer;
        this.metrics = metrics;
    }

    @Transactional
    public Map<String, Object> reserveSeats(String userId, SeatReserveRequest request) {
        if (request.seatIds() == null || request.seatIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please select at least one seat");
        }

        // Idempotency check: return existing reservation if key matches
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id, reservation_number, total_amount, status, payment_status, expires_at FROM reservations WHERE idempotency_key = ?",
                request.idempotencyKey());
            if (!existing.isEmpty()) {
                return Map.of("message", "Seat reserved temporarily", "reservation", existing.getFirst());
            }
        }
        if (request.seatIds().size() > MAX_SEATS_PER_RESERVATION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only one seat can be selected");
        }

        // Phase 1: Acquire Redis Lua seat locks (before DB lock)
        List<SeatLockService.SeatLockResult> lockResults = new ArrayList<>();
        for (UUID seatId : request.seatIds()) {
            SeatLockService.SeatLockResult lockResult = seatLockService.acquireLock(
                request.eventId(), seatId, userId);

            if (!lockResult.success()) {
                // Rollback any already-acquired locks
                for (int i = 0; i < lockResults.size(); i++) {
                    seatLockService.releaseLock(request.eventId(),
                        request.seatIds().get(i), userId, lockResults.get(i).fencingToken());
                }
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat already selected by another user");
            }
            lockResults.add(lockResult);
        }

        // Phase 2: DB lock with optimistic locking (version check)
        List<Map<String, Object>> seats = namedParameterJdbcTemplate.queryForList("""
            SELECT id, seat_label, price, status, version
            FROM seats
            WHERE id IN (:seatIds) AND event_id = :eventId
            FOR UPDATE
            """, new MapSqlParameterSource()
            .addValue("seatIds", request.seatIds())
            .addValue("eventId", request.eventId()));

        if (seats.size() != request.seatIds().size()) {
            releaseLocks(request.eventId(), request.seatIds(), userId, lockResults);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Selected seat not found");
        }

        List<String> unavailable = seats.stream()
            .filter(seat -> !Objects.equals("available", seat.get("status")))
            .map(seat -> String.valueOf(seat.get("seat_label")))
            .toList();

        if (!unavailable.isEmpty()) {
            releaseLocks(request.eventId(), request.seatIds(), userId, lockResults);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat already reserved: " + String.join(", ", unavailable));
        }

        // Phase 3: Update seats with version increment and fencing token
        for (int i = 0; i < seats.size(); i++) {
            Map<String, Object> seat = seats.get(i);
            int currentVersion = ((Number) seat.get("version")).intValue();
            long fencingToken = lockResults.get(i).fencingToken();

            int updated = jdbcTemplate.update("""
                UPDATE seats
                SET status = 'locked', version = version + 1,
                    fencing_token = ?, locked_by = CAST(? AS UUID), updated_at = NOW()
                WHERE id = ? AND version = ?
                """, fencingToken, userId, seat.get("id"), currentVersion);

            if (updated == 0) {
                releaseLocks(request.eventId(), request.seatIds(), userId, lockResults);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat modified concurrently");
            }
        }

        int totalAmount = seats.stream().mapToInt(s -> ((Number) s.get("price")).intValue()).sum();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
        String reservationNumber = "TK" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        UUID reservationId = jdbcTemplate.queryForObject("""
            INSERT INTO reservations (user_id, event_id, reservation_number, total_amount, status, payment_status, expires_at, idempotency_key)
            VALUES (CAST(? AS UUID), ?, ?, ?, 'pending', 'pending', ?, ?)
            RETURNING id
            """, UUID.class, userId, request.eventId(), reservationNumber, totalAmount,
            Timestamp.from(expiresAt.toInstant()),
            request.idempotencyKey() != null && !request.idempotencyKey().isBlank() ? request.idempotencyKey() : null);

        for (Map<String, Object> seat : seats) {
            jdbcTemplate.update("""
                INSERT INTO reservation_items (reservation_id, ticket_type_id, quantity, unit_price, subtotal, seat_id)
                VALUES (?, NULL, 1, ?, ?, ?)
                """, reservationId, seat.get("price"), seat.get("price"), seat.get("id"));
        }

        // Build fencing token map for response
        long primaryToken = lockResults.isEmpty() ? -1 : lockResults.getFirst().fencingToken();

        Map<String, Object> reservation = new HashMap<>();
        reservation.put("id", reservationId);
        reservation.put("reservationNumber", reservationNumber);
        reservation.put("totalAmount", totalAmount);
        reservation.put("expiresAt", expiresAt);
        reservation.put("seats", seats);
        reservation.put("fencingToken", primaryToken);

        metrics.recordReservationCreated();
        return Map.of("message", "Seat reserved temporarily", "reservation", reservation);
    }

    private void releaseLocks(UUID eventId, List<UUID> seatIds, String userId,
                               List<SeatLockService.SeatLockResult> lockResults) {
        for (int i = 0; i < lockResults.size(); i++) {
            seatLockService.releaseLock(eventId, seatIds.get(i), userId, lockResults.get(i).fencingToken());
        }
    }

    @Transactional
    public Map<String, Object> createReservation(String userId, CreateReservationRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation items are required");
        }

        // Idempotency check: return existing reservation if key matches
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id, reservation_number, total_amount, status, payment_status, expires_at FROM reservations WHERE idempotency_key = ?",
                request.idempotencyKey());
            if (!existing.isEmpty()) {
                return Map.of("message", "Reservation created", "reservation", existing.getFirst());
            }
        }

        List<Map<String, Object>> eventRows = jdbcTemplate.queryForList("SELECT id, title FROM events WHERE id = ?", request.eventId());
        if (eventRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }

        int totalAmount = 0;
        List<Map<String, Object>> reservationItems = new ArrayList<>();

        for (ReservationItemRequest item : request.items()) {
            List<Map<String, Object>> ticketRows = jdbcTemplate.queryForList("""
                SELECT id, name, price, available_quantity
                FROM ticket_types
                WHERE id = ? AND event_id = ?
                FOR UPDATE
                """, item.ticketTypeId(), request.eventId());

            if (ticketRows.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket type not found: " + item.ticketTypeId());
            }

            Map<String, Object> ticket = ticketRows.getFirst();
            int available = ((Number) ticket.get("available_quantity")).intValue();
            if (available < item.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ticket.get("name") + " quantity is not enough");
            }

            jdbcTemplate.update("UPDATE ticket_types SET available_quantity = available_quantity - ? WHERE id = ?",
                item.quantity(), item.ticketTypeId());

            int price = ((Number) ticket.get("price")).intValue();
            int subtotal = price * item.quantity();
            totalAmount += subtotal;

            reservationItems.add(Map.of(
                "ticketTypeId", ticket.get("id"),
                "ticketTypeName", ticket.get("name"),
                "quantity", item.quantity(),
                "unitPrice", price,
                "subtotal", subtotal));
        }

        String reservationNumber = "R" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);

        UUID reservationId = jdbcTemplate.queryForObject("""
            INSERT INTO reservations (user_id, event_id, reservation_number, total_amount, status, payment_status, expires_at, idempotency_key)
            VALUES (CAST(? AS UUID), ?, ?, ?, 'pending', 'pending', ?, ?)
            RETURNING id
            """, UUID.class, userId, request.eventId(), reservationNumber, totalAmount,
            Timestamp.from(expiresAt.toInstant()),
            request.idempotencyKey() != null && !request.idempotencyKey().isBlank() ? request.idempotencyKey() : null);

        for (Map<String, Object> item : reservationItems) {
            jdbcTemplate.update("""
                INSERT INTO reservation_items (reservation_id, ticket_type_id, quantity, unit_price, subtotal)
                VALUES (?, ?, ?, ?, ?)
                """, reservationId, item.get("ticketTypeId"), item.get("quantity"), item.get("unitPrice"), item.get("subtotal"));
        }

        Map<String, Object> reservation = new HashMap<>();
        reservation.put("id", reservationId);
        reservation.put("reservationNumber", reservationNumber);
        reservation.put("eventTitle", eventRows.getFirst().get("title"));
        reservation.put("totalAmount", totalAmount);
        reservation.put("status", "pending");
        reservation.put("paymentStatus", "pending");
        reservation.put("expiresAt", expiresAt);
        reservation.put("items", reservationItems);

        metrics.recordReservationCreated();
        return Map.of("message", "Reservation created", "reservation", reservation);
    }

    public Map<String, Object> getMyReservations(String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT
              r.id, r.reservation_number, r.total_amount, r.status, r.payment_status,
              r.created_at, r.expires_at,
              e.title as event_title, e.venue, e.event_date,
              json_agg(
                json_build_object(
                  'ticketTypeName', COALESCE(tt.name, s.seat_label),
                  'quantity', COALESCE(ri.quantity, 1),
                  'unitPrice', ri.unit_price,
                  'subtotal', ri.subtotal,
                  'seatLabel', s.seat_label
                )
              )::text as items
            FROM reservations r
            LEFT JOIN events e ON r.event_id = e.id
            JOIN reservation_items ri ON r.id = ri.reservation_id
            LEFT JOIN ticket_types tt ON ri.ticket_type_id = tt.id
            LEFT JOIN seats s ON ri.seat_id = s.id
            WHERE r.user_id = CAST(? AS UUID)
            GROUP BY r.id, e.id
            ORDER BY r.created_at DESC
            """, userId);
        return Map.of("reservations", rows);
    }

    public Map<String, Object> getReservationById(String userId, UUID reservationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT
              r.id, r.reservation_number, r.total_amount, r.status,
              r.payment_status, r.payment_method, r.expires_at, r.created_at,
              e.id as event_id, e.title as event_title, e.venue, e.address, e.event_date,
              json_agg(
                json_build_object(
                  'ticketTypeName', COALESCE(tt.name, s.seat_label),
                  'quantity', COALESCE(ri.quantity, 1),
                  'unitPrice', ri.unit_price,
                  'subtotal', ri.subtotal,
                  'seatId', s.id,
                  'seatLabel', s.seat_label,
                  'section', s.section,
                  'rowNumber', s.row_number,
                  'seatNumber', s.seat_number
                )
              )::text as items
            FROM reservations r
            JOIN events e ON r.event_id = e.id
            JOIN reservation_items ri ON r.id = ri.reservation_id
            LEFT JOIN ticket_types tt ON ri.ticket_type_id = tt.id
            LEFT JOIN seats s ON ri.seat_id = s.id
            WHERE r.id = ? AND r.user_id = CAST(? AS UUID)
            GROUP BY r.id, e.id
            """, reservationId, userId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }
        return Map.of("reservation", rows.getFirst());
    }

    public Map<String, Object> getSeatReservationDetail(String userId, UUID reservationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT
              r.id, r.reservation_number, r.total_amount, r.status,
              r.payment_status, r.payment_method, r.expires_at, r.created_at,
              e.id as event_id, e.title as event_title, e.venue, e.event_date,
              json_agg(
                json_build_object(
                  'seatId', s.id,
                  'seatLabel', s.seat_label,
                  'section', s.section,
                  'rowNumber', s.row_number,
                  'seatNumber', s.seat_number,
                  'price', ri.unit_price,
                  'ticketTypeName', tt.name,
                  'quantity', ri.quantity
                )
              )::text as seats
            FROM reservations r
            JOIN events e ON r.event_id = e.id
            JOIN reservation_items ri ON r.id = ri.reservation_id
            LEFT JOIN seats s ON ri.seat_id = s.id
            LEFT JOIN ticket_types tt ON ri.ticket_type_id = tt.id
            WHERE r.id = ? AND r.user_id = CAST(? AS UUID)
            GROUP BY r.id, e.id
            """, reservationId, userId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }
        return Map.of("reservation", rows.getFirst());
    }

    public Map<String, Object> validatePendingReservation(UUID reservationId, String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT id, user_id, event_id, reservation_number, total_amount, status, payment_status, expires_at
            FROM reservations
            WHERE id = ?
            """, reservationId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }

        Map<String, Object> reservation = rows.getFirst();
        if (!Objects.equals(String.valueOf(reservation.get("user_id")), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        String status = String.valueOf(reservation.get("status"));
        if (!"pending".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation is not pending");
        }

        Object expiresAt = reservation.get("expires_at");
        if (expiresAt instanceof java.sql.Timestamp ts && ts.toInstant().isBefore(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation has expired");
        }
        if (expiresAt instanceof java.time.OffsetDateTime odt && odt.toInstant().isBefore(java.time.Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reservation has expired");
        }

        return reservation;
    }

    @Transactional
    public void confirmReservationPayment(UUID reservationId, String paymentMethod) {
        // Get reservation + event info for Redis lock verification
        List<Map<String, Object>> resRows = jdbcTemplate.queryForList(
            "SELECT r.user_id, r.event_id, e.artist_id FROM reservations r JOIN events e ON r.event_id = e.id WHERE r.id = ?",
            reservationId);

        if (!resRows.isEmpty()) {
            String userId = String.valueOf(resRows.getFirst().get("user_id"));
            UUID eventId = (UUID) resRows.getFirst().get("event_id");

            // Verify fencing tokens via Redis for each seat
            List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT ri.seat_id, s.fencing_token FROM reservation_items ri LEFT JOIN seats s ON ri.seat_id = s.id WHERE ri.reservation_id = ?",
                reservationId);

            for (Map<String, Object> item : items) {
                Object seatIdObj = item.get("seat_id");
                Object tokenObj = item.get("fencing_token");
                if (seatIdObj != null && tokenObj != null) {
                    UUID seatId = (UUID) seatIdObj;
                    long token = ((Number) tokenObj).longValue();
                    if (token > 0 && !seatLockService.verifyForPayment(eventId, seatId, userId, token)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat lock expired or stolen. Please try again.");
                    }
                }
            }
        }

        int updated = jdbcTemplate.update("""
            UPDATE reservations
            SET status = 'confirmed',
                payment_status = 'completed',
                payment_method = ?,
                updated_at = NOW()
            WHERE id = ?
              AND status = 'pending'
            """, paymentMethod, reservationId);

        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation cannot be confirmed");
        }

        // Update seats to reserved and clean up Redis locks
        List<Map<String, Object>> seatItems = jdbcTemplate.queryForList(
            "SELECT seat_id FROM reservation_items WHERE reservation_id = ? AND seat_id IS NOT NULL", reservationId);

        UUID eventId = resRows.isEmpty() ? null : (UUID) resRows.getFirst().get("event_id");

        for (Map<String, Object> item : seatItems) {
            UUID seatId = (UUID) item.get("seat_id");
            jdbcTemplate.update("UPDATE seats SET status = 'reserved', updated_at = NOW() WHERE id = ?", seatId);
            // Clean up Redis seat lock
            if (eventId != null) {
                seatLockService.cleanupLock(eventId, seatId);
            }
        }

        // Award membership points for ticket purchase
        try {
            if (!resRows.isEmpty() && resRows.getFirst().get("artist_id") != null) {
                UUID artistId = (UUID) resRows.getFirst().get("artist_id");
                String userId = String.valueOf(resRows.getFirst().get("user_id"));
                membershipService.awardPointsForArtist(userId, artistId, "TICKET_PURCHASE", 100,
                    "Points for ticket purchase", reservationId);
            }
        } catch (Exception e) {
            log.warn("Failed to award membership points for reservation {}: {}", reservationId, e.getMessage());
        }
    }

    @Transactional
    public void markReservationRefunded(UUID reservationId) {
        jdbcTemplate.update("""
            UPDATE reservations
            SET status = 'cancelled',
                payment_status = 'refunded',
                updated_at = NOW()
            WHERE id = ?
            """, reservationId);

        jdbcTemplate.update("""
            UPDATE seats
            SET status = 'available', updated_at = NOW()
            WHERE id IN (
                SELECT seat_id FROM reservation_items
                WHERE reservation_id = ? AND seat_id IS NOT NULL
            )
            """, reservationId);
    }

    @Transactional
    public Map<String, Object> cancelReservation(String userId, UUID reservationId) {
        List<Map<String, Object>> reservationRows = jdbcTemplate.queryForList(
            "SELECT id, status, event_id FROM reservations WHERE id = ? AND user_id = CAST(? AS UUID) FOR UPDATE",
            reservationId, userId);

        if (reservationRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
        }

        String status = String.valueOf(reservationRows.getFirst().get("status"));
        if ("cancelled".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already cancelled reservation");
        }

        UUID eventId = (UUID) reservationRows.getFirst().get("event_id");

        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT ticket_type_id, quantity, seat_id FROM reservation_items WHERE reservation_id = ?", reservationId);

        for (Map<String, Object> item : items) {
            Object ticketTypeId = item.get("ticket_type_id");
            Number quantity = (Number) item.get("quantity");
            Object seatId = item.get("seat_id");

            if (ticketTypeId != null) {
                jdbcTemplate.update("UPDATE ticket_types SET available_quantity = available_quantity + ? WHERE id = ?",
                    quantity, ticketTypeId);
            }

            if (seatId != null) {
                jdbcTemplate.update("UPDATE seats SET status = 'available', updated_at = NOW() WHERE id = ?", seatId);
                // Clean up Redis seat lock
                if (eventId != null) {
                    seatLockService.cleanupLock(eventId, (UUID) seatId);
                }
            }
        }

        // Set payment_status to 'refund_requested' — actual refund is handled by payment-service
        // via Kafka event. Do NOT set 'refunded' directly as no actual money movement occurs here.
        jdbcTemplate.update("UPDATE reservations SET status = 'cancelled', payment_status = 'refund_requested', updated_at = NOW() WHERE id = ?",
            reservationId);

        metrics.recordReservationCancelled();

        // Publish cancellation event so payment-service can process the actual refund
        if (ticketEventProducer != null) {
            ticketEventProducer.publishReservationCancelled(new com.tiketi.ticketservice.messaging.event.ReservationCancelledEvent(
                reservationId, userId, eventId, "User requested cancellation", Instant.now()));
        }

        return Map.of("message", "Reservation cancelled", "reservation", Map.of("id", reservationId, "status", "cancelled"));
    }
}
