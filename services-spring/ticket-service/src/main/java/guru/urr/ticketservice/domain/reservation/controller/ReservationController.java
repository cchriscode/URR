package guru.urr.ticketservice.domain.reservation.controller;

import guru.urr.ticketservice.domain.reservation.dto.CreateReservationRequest;
import guru.urr.ticketservice.shared.security.AuthUser;
import guru.urr.ticketservice.shared.security.JwtTokenParser;
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
        HttpServletRequest request,
        @Valid @RequestBody CreateReservationRequest body
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return reservationService.createReservation(user.userId(), body);
    }

    @GetMapping("/my")
    public Map<String, Object> my(HttpServletRequest request) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return reservationService.getMyReservations(user.userId());
    }

    @GetMapping("/{id}")
    public Map<String, Object> byId(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return reservationService.getReservationById(user.userId(), id);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return reservationService.cancelReservation(user.userId(), id);
    }
}
