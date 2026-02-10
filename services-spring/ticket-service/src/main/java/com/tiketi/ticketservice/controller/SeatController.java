package com.tiketi.ticketservice.controller;

import com.tiketi.ticketservice.dto.SeatReserveRequest;
import com.tiketi.ticketservice.security.AuthUser;
import com.tiketi.ticketservice.security.JwtTokenParser;
import com.tiketi.ticketservice.service.EventReadService;
import com.tiketi.ticketservice.service.ReservationService;
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
@RequestMapping("/api/seats")
public class SeatController {

    private final EventReadService eventReadService;
    private final ReservationService reservationService;
    private final JwtTokenParser jwtTokenParser;

    public SeatController(EventReadService eventReadService, ReservationService reservationService, JwtTokenParser jwtTokenParser) {
        this.eventReadService = eventReadService;
        this.reservationService = reservationService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping("/layouts")
    public Map<String, Object> layouts() {
        return eventReadService.getSeatLayouts();
    }

    @GetMapping("/events/{eventId}")
    public Map<String, Object> byEvent(@PathVariable UUID eventId) {
        return eventReadService.getSeatsByEvent(eventId);
    }

    @PostMapping("/reserve")
    public Map<String, Object> reserve(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody SeatReserveRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return reservationService.reserveSeats(user.userId(), request);
    }

    @GetMapping("/reservation/{reservationId}")
    public Map<String, Object> reservationDetail(
        @PathVariable UUID reservationId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return reservationService.getSeatReservationDetail(user.userId(), reservationId);
    }
}
