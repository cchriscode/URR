package com.tiketi.ticketservice.internal.controller;

import com.tiketi.ticketservice.domain.membership.service.MembershipService;
import com.tiketi.ticketservice.internal.dto.AwardPointsRequest;
import com.tiketi.ticketservice.shared.security.InternalTokenValidator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/memberships")
public class InternalMembershipController {

    private static final Logger log = LoggerFactory.getLogger(InternalMembershipController.class);

    private final MembershipService membershipService;
    private final InternalTokenValidator internalTokenValidator;

    public InternalMembershipController(MembershipService membershipService,
                                         InternalTokenValidator internalTokenValidator) {
        this.membershipService = membershipService;
        this.internalTokenValidator = internalTokenValidator;
    }

    @PostMapping("/award-points")
    public Map<String, Object> awardPoints(
            @RequestBody AwardPointsRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        try {
            membershipService.awardPointsToAllMemberships(
                request.userId(), request.actionType(), request.points(),
                request.description(), request.referenceId());
            return Map.of("ok", true);
        } catch (Exception e) {
            log.warn("Points award failed: {}", e.getMessage());
            return Map.of("ok", false, "error", e.getMessage());
        }
    }
}
