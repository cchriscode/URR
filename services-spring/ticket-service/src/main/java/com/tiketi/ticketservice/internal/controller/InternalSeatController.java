package com.tiketi.ticketservice.internal.controller;

import com.tiketi.ticketservice.domain.seat.service.SeatGeneratorService;
import com.tiketi.ticketservice.domain.seat.service.SeatLockService;
import com.tiketi.ticketservice.domain.seat.service.SeatLockService.SeatLockResult;
import com.tiketi.ticketservice.internal.dto.InternalSeatReserveRequest;
import com.tiketi.ticketservice.shared.security.InternalTokenValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/seats")
public class InternalSeatController {

    private final SeatLockService seatLockService;
    private final SeatGeneratorService seatGeneratorService;
    private final InternalTokenValidator internalTokenValidator;

    public InternalSeatController(SeatLockService seatLockService,
                                   SeatGeneratorService seatGeneratorService,
                                   InternalTokenValidator internalTokenValidator) {
        this.seatLockService = seatLockService;
        this.seatGeneratorService = seatGeneratorService;
        this.internalTokenValidator = internalTokenValidator;
    }

    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody InternalSeatReserveRequest request) {

        internalTokenValidator.requireValidToken(authorization);

        List<Map<String, Object>> results = new ArrayList<>();
        boolean allSuccess = true;

        for (UUID seatId : request.seatIds()) {
            SeatLockResult lockResult = seatLockService.acquireLock(
                    request.eventId(), seatId, request.userId());

            Map<String, Object> seatResult = new HashMap<>();
            seatResult.put("seatId", seatId);
            seatResult.put("success", lockResult.success());
            seatResult.put("fencingToken", lockResult.fencingToken());
            results.add(seatResult);

            if (!lockResult.success()) {
                allSuccess = false;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", allSuccess);
        response.put("seats", results);

        return allSuccess
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(409).body(response);
    }

    @PostMapping("/generate/{eventId}/{layoutId}")
    public Map<String, Object> generateSeats(
            @PathVariable UUID eventId,
            @PathVariable UUID layoutId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        int created = seatGeneratorService.generateSeatsForEvent(eventId, layoutId);
        return Map.of("seatsCreated", created);
    }

    @GetMapping("/count/{eventId}")
    public Map<String, Object> countSeats(
            @PathVariable UUID eventId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        int count = seatGeneratorService.countSeats(eventId);
        return Map.of("count", count);
    }

    @DeleteMapping("/{eventId}")
    public Map<String, Object> deleteSeats(
            @PathVariable UUID eventId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        int deleted = seatGeneratorService.deleteSeatsForEvent(eventId);
        return Map.of("deleted", deleted);
    }
}
