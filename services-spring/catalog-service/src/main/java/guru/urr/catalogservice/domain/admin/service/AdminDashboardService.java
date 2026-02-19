package guru.urr.catalogservice.domain.admin.service;

import guru.urr.catalogservice.domain.admin.dto.AdminReservationStatusRequest;
import guru.urr.catalogservice.domain.admin.dto.AdminTicketTypeRequest;
import guru.urr.catalogservice.domain.admin.dto.AdminTicketUpdateRequest;
import guru.urr.catalogservice.shared.client.AuthInternalClient;
import guru.urr.catalogservice.shared.client.TicketInternalClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final TicketInternalClient ticketInternalClient;
    private final AuthInternalClient authInternalClient;

    public AdminDashboardService(JdbcTemplate jdbcTemplate,
                                  TicketInternalClient ticketInternalClient,
                                  AuthInternalClient authInternalClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.ticketInternalClient = ticketInternalClient;
        this.authInternalClient = authInternalClient;
    }

    public Map<String, Object> dashboardStats() {
        int totalEvents = intValue(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM events", Integer.class));

        Map<String, Object> reservationStats = ticketInternalClient.getReservationStats();
        int totalReservations = intValue(reservationStats.get("totalReservations"));
        int totalRevenue = intValue(reservationStats.get("totalRevenue"));
        int todayReservations = intValue(reservationStats.get("todayReservations"));

        List<Map<String, Object>> recent = ticketInternalClient.getRecentReservations();

        hydrateUserInfo(recent);

        return Map.of(
            "stats", Map.of(
                "totalEvents", totalEvents,
                "totalReservations", totalReservations,
                "totalRevenue", totalRevenue,
                "todayReservations", todayReservations
            ),
            "recentReservations", recent
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> listReservations(Integer page, Integer limit, String status) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);

        Map<String, Object> result = ticketInternalClient.listReservations(safePage, safeLimit, status);

        List<Map<String, Object>> reservations = result.get("reservations") instanceof List<?> list
            ? (List<Map<String, Object>>) list
            : List.of();

        hydrateUserInfo(reservations);

        Map<String, Object> response = new HashMap<>();
        response.put("reservations", reservations);
        response.put("pagination", result.get("pagination"));
        return response;
    }

    public Map<String, Object> updateReservationStatus(UUID reservationId, AdminReservationStatusRequest request) {
        if ((request.status() == null || request.status().isBlank())
            && (request.paymentStatus() == null || request.paymentStatus().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No status fields to update");
        }

        Map<String, Object> result = ticketInternalClient.updateReservationStatus(
            reservationId, request.status(), request.paymentStatus());
        return Map.of("message", "Reservation status updated", "reservation", result.get("reservation"));
    }

    public Map<String, Object> createTicketType(UUID eventId, AdminTicketTypeRequest request) {
        Map<String, Object> result = ticketInternalClient.createTicketType(
            eventId, request.name(), request.price(), request.totalQuantity(), request.description());
        return Map.of("message", "Ticket type created", "ticketType", result.get("ticketType"));
    }

    public Map<String, Object> updateTicketType(UUID ticketTypeId, AdminTicketUpdateRequest request) {
        Map<String, Object> result = ticketInternalClient.updateTicketType(
            ticketTypeId, request.name(), request.price(), request.totalQuantity(), request.description());
        return Map.of("message", "Ticket type updated", "ticketType", result.get("ticketType"));
    }

    private void hydrateUserInfo(List<Map<String, Object>> reservations) {
        List<UUID> userIds = reservations.stream()
            .map(r -> r.get("user_id"))
            .filter(Objects::nonNull)
            .map(v -> v instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(v)))
            .distinct()
            .toList();

        Map<UUID, Map<String, Object>> users = authInternalClient.findUsersByIds(userIds);
        for (Map<String, Object> reservation : reservations) {
            Object rawUserId = reservation.get("user_id");
            if (rawUserId == null) {
                reservation.put("user_name", null);
                reservation.put("user_email", null);
                continue;
            }
            UUID userId = rawUserId instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(rawUserId));
            Map<String, Object> user = users.get(userId);
            reservation.put("user_name", user != null ? user.get("name") : null);
            reservation.put("user_email", user != null ? user.get("email") : null);
        }
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
