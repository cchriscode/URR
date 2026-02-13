package com.tiketi.ticketservice.domain.reservation.controller;

import com.tiketi.ticketservice.domain.reservation.dto.CreateReservationRequest;
import com.tiketi.ticketservice.shared.security.AuthUser;
import com.tiketi.ticketservice.shared.security.JwtTokenParser;
import com.tiketi.ticketservice.domain.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final JwtTokenParser jwtTokenParser;

    public ReservationController(ReservationService reservationService, JwtTokenParser jwtTokenParser) {
        this.reservationService = reservationService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @PostMapping
    public Map<String, Object> create(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody CreateReservationRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return reservationService.createReservation(user.userId(), request);
    }

    @GetMapping("/my")
    public Map<String, Object> my(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return reservationService.getMyReservations(user.userId());
    }

    @GetMapping("/{id}")
    public Map<String, Object> byId(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return reservationService.getReservationById(user.userId(), id);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return reservationService.cancelReservation(user.userId(), id);
    }
}
