package com.tiketi.ticketservice.controller;

import com.tiketi.ticketservice.dto.AdminEventRequest;
import com.tiketi.ticketservice.dto.AdminReservationStatusRequest;
import com.tiketi.ticketservice.dto.AdminTicketTypeRequest;
import com.tiketi.ticketservice.dto.AdminTicketUpdateRequest;
import com.tiketi.ticketservice.security.AuthUser;
import com.tiketi.ticketservice.security.JwtTokenParser;
import com.tiketi.ticketservice.service.AdminService;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
    public Map<String, Object> dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.dashboardStats();
    }

    @GetMapping("/seat-layouts")
    public Map<String, Object> seatLayouts(@RequestHeader(value = "Authorization", required = false) String authorization) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.seatLayouts();
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> createEvent(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody AdminEventRequest request
    ) {
        AuthUser admin = jwtTokenParser.requireAdmin(authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createEvent(request, admin.userId()));
    }

    @PutMapping("/events/{id}")
    public Map<String, Object> updateEvent(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody AdminEventRequest request
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.updateEvent(id, request);
    }

    @PostMapping("/events/{id}/cancel")
    public Map<String, Object> cancelEvent(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.cancelEvent(id);
    }

    @DeleteMapping("/events/{id}")
    public Map<String, Object> deleteEvent(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.deleteEvent(id);
    }

    @PostMapping("/events/{id}/generate-seats")
    public Map<String, Object> generateSeats(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.generateSeats(id);
    }

    @DeleteMapping("/events/{id}/seats")
    public Map<String, Object> deleteSeats(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.deleteSeats(id);
    }

    @PostMapping("/events/{eventId}/tickets")
    public ResponseEntity<Map<String, Object>> createTicket(
        @PathVariable UUID eventId,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody AdminTicketTypeRequest request
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTicketType(eventId, request));
    }

    @PutMapping("/tickets/{id}")
    public Map<String, Object> updateTicket(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody AdminTicketUpdateRequest request
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.updateTicketType(id, request);
    }

    @GetMapping("/reservations")
    public Map<String, Object> reservations(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String status
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.listReservations(page, limit, status);
    }

    @PatchMapping("/reservations/{id}/status")
    public Map<String, Object> updateReservationStatus(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody AdminReservationStatusRequest request
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return adminService.updateReservationStatus(id, request);
    }
}
