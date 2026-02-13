package com.tiketi.ticketservice.internal.controller;

import com.tiketi.ticketservice.shared.security.InternalTokenValidator;
import com.tiketi.ticketservice.domain.membership.service.MembershipService;
import com.tiketi.ticketservice.domain.reservation.service.ReservationService;
import com.tiketi.ticketservice.domain.transfer.service.TransferService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalReservationController {

    private final InternalTokenValidator internalTokenValidator;
    private final ReservationService reservationService;
    private final TransferService transferService;
    private final MembershipService membershipService;

    public InternalReservationController(
        InternalTokenValidator internalTokenValidator,
        ReservationService reservationService,
        TransferService transferService,
        MembershipService membershipService
    ) {
        this.internalTokenValidator = internalTokenValidator;
        this.reservationService = reservationService;
        this.transferService = transferService;
        this.membershipService = membershipService;
    }

    // === Reservation internal APIs ===

    @GetMapping("/reservations/{id}/validate")
    public Map<String, Object> validate(
        @PathVariable UUID id,
        @RequestParam String userId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        internalTokenValidator.requireValidToken(authorization);
        return reservationService.validatePendingReservation(id, userId);
    }

    @PostMapping("/reservations/{id}/confirm")
    public Map<String, Object> confirm(
        @PathVariable UUID id,
        @RequestBody(required = false) Map<String, Object> payload,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        internalTokenValidator.requireValidToken(authorization);
        String paymentMethod = payload != null && payload.get("paymentMethod") != null ? String.valueOf(payload.get("paymentMethod")) : "toss";
        reservationService.confirmReservationPayment(id, paymentMethod);
        return Map.of("ok", true);
    }

    @PostMapping("/reservations/{id}/refund")
    public Map<String, Object> refund(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        internalTokenValidator.requireValidToken(authorization);
        reservationService.markReservationRefunded(id);
        return Map.of("ok", true);
    }

    // === Transfer internal APIs ===

    @GetMapping("/transfers/{id}/validate")
    public Map<String, Object> validateTransfer(
        @PathVariable UUID id,
        @RequestParam String userId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        internalTokenValidator.requireValidToken(authorization);
        return transferService.validateForPurchase(id, userId);
    }

    @PostMapping("/transfers/{id}/complete")
    public Map<String, Object> completeTransfer(
        @PathVariable UUID id,
        @RequestBody(required = false) Map<String, Object> payload,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        internalTokenValidator.requireValidToken(authorization);
        String buyerId = payload != null ? String.valueOf(payload.get("buyerId")) : null;
        String paymentMethod = payload != null ? String.valueOf(payload.get("paymentMethod")) : "toss";
        transferService.completePurchase(id, buyerId, paymentMethod);
        return Map.of("ok", true);
    }

    // === Membership internal APIs ===

    @GetMapping("/memberships/{id}/validate")
    public Map<String, Object> validateMembership(
        @PathVariable UUID id,
        @RequestParam String userId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        internalTokenValidator.requireValidToken(authorization);
        return membershipService.validatePendingMembership(id, userId);
    }

    @PostMapping("/memberships/{id}/activate")
    public Map<String, Object> activateMembership(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        internalTokenValidator.requireValidToken(authorization);
        membershipService.activateMembership(id);
        return Map.of("ok", true);
    }
}
