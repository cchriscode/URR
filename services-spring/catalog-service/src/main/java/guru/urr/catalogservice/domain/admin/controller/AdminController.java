package guru.urr.catalogservice.domain.admin.controller;

import guru.urr.catalogservice.domain.admin.dto.AdminEventRequest;
import guru.urr.catalogservice.domain.admin.dto.AdminReservationStatusRequest;
import guru.urr.catalogservice.domain.admin.dto.AdminTicketTypeRequest;
import guru.urr.catalogservice.domain.admin.dto.AdminTicketUpdateRequest;
import guru.urr.catalogservice.shared.audit.AuditLog;
import guru.urr.catalogservice.shared.security.AuthUser;
import guru.urr.catalogservice.shared.security.JwtTokenParser;
import guru.urr.catalogservice.domain.admin.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final JwtTokenParser jwtTokenParser;

    public AdminController(AdminService adminService, JwtTokenParser jwtTokenParser) {
        this.adminService = adminService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping({"/dashboard", "/dashboard/stats"})
    public Map<String, Object> dashboard(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return adminService.dashboardStats();
    }

    @GetMapping("/seat-layouts")
    public Map<String, Object> seatLayouts(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return adminService.seatLayouts();
    }

    @AuditLog(action = "CREATE_EVENT", resourceType = "event")
    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> createEvent(
        HttpServletRequest request,
        @Valid @RequestBody AdminEventRequest body
    ) {
        AuthUser admin = jwtTokenParser.requireAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createEvent(body, admin.userId()));
    }

    @AuditLog(action = "UPDATE_EVENT", resourceType = "event")
    @PutMapping("/events/{id}")
    public Map<String, Object> updateEvent(
        @PathVariable UUID id,
        HttpServletRequest request,
        @Valid @RequestBody AdminEventRequest body
    ) {
        jwtTokenParser.requireAdmin(request);
        return adminService.updateEvent(id, body);
    }

    @AuditLog(action = "CANCEL_EVENT", resourceType = "event")
    @PostMapping("/events/{id}/cancel")
    public Map<String, Object> cancelEvent(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireAdmin(request);
        return adminService.cancelEvent(id);
    }

    @AuditLog(action = "DELETE_EVENT", resourceType = "event")
    @DeleteMapping("/events/{id}")
    public Map<String, Object> deleteEvent(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireAdmin(request);
        return adminService.deleteEvent(id);
    }

    @AuditLog(action = "GENERATE_SEATS", resourceType = "event")
    @PostMapping("/events/{id}/generate-seats")
    public Map<String, Object> generateSeats(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireAdmin(request);
        return adminService.generateSeats(id);
    }

    @AuditLog(action = "DELETE_SEATS", resourceType = "event")
    @DeleteMapping("/events/{id}/seats")
    public Map<String, Object> deleteSeats(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireAdmin(request);
        return adminService.deleteSeats(id);
    }

    @AuditLog(action = "CREATE_TICKET_TYPE", resourceType = "ticket_type")
    @PostMapping("/events/{eventId}/tickets")
    public ResponseEntity<Map<String, Object>> createTicket(
        @PathVariable UUID eventId,
        HttpServletRequest request,
        @Valid @RequestBody AdminTicketTypeRequest body
    ) {
        jwtTokenParser.requireAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTicketType(eventId, body));
    }

    @AuditLog(action = "UPDATE_TICKET_TYPE", resourceType = "ticket_type")
    @PutMapping("/tickets/{id}")
    public Map<String, Object> updateTicket(
        @PathVariable UUID id,
        HttpServletRequest request,
        @Valid @RequestBody AdminTicketUpdateRequest body
    ) {
        jwtTokenParser.requireAdmin(request);
        return adminService.updateTicketType(id, body);
    }

    @GetMapping("/reservations")
    public Map<String, Object> reservations(
        HttpServletRequest request,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String status
    ) {
        jwtTokenParser.requireAdmin(request);
        return adminService.listReservations(page, limit, status);
    }

    @AuditLog(action = "UPDATE_RESERVATION_STATUS", resourceType = "reservation")
    @PatchMapping("/reservations/{id}/status")
    public Map<String, Object> updateReservationStatus(
        @PathVariable UUID id,
        HttpServletRequest request,
        @RequestBody AdminReservationStatusRequest body
    ) {
        jwtTokenParser.requireAdmin(request);
        return adminService.updateReservationStatus(id, body);
    }
}
