package guru.urr.ticketservice.domain.seat.controller;

import guru.urr.ticketservice.domain.reservation.dto.SeatReserveRequest;
import guru.urr.common.security.AuthUser;
import guru.urr.common.security.JwtTokenParser;
import guru.urr.ticketservice.shared.service.CatalogReadService;
import guru.urr.ticketservice.domain.reservation.service.ReservationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seats")
public class SeatController {

    private final CatalogReadService catalogReadService;
    private final ReservationService reservationService;
    private final JwtTokenParser jwtTokenParser;

    public SeatController(CatalogReadService catalogReadService, ReservationService reservationService, JwtTokenParser jwtTokenParser) {
        this.catalogReadService = catalogReadService;
        this.reservationService = reservationService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping("/layouts")
    public Map<String, Object> layouts() {
        return catalogReadService.getSeatLayouts();
    }

    @GetMapping("/events/{eventId}")
    public Map<String, Object> byEvent(@PathVariable UUID eventId) {
        return catalogReadService.getSeatsByEvent(eventId);
    }

    @PostMapping("/reserve")
    public Map<String, Object> reserve(
        HttpServletRequest request,
        @Valid @RequestBody SeatReserveRequest body
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return reservationService.reserveSeats(user.userId(), body);
    }

    @GetMapping("/reservation/{reservationId}")
    public Map<String, Object> reservationDetail(
        @PathVariable UUID reservationId,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return reservationService.getSeatReservationDetail(user.userId(), reservationId);
    }
}
