package com.tiketi.ticketservice.controller;

import com.tiketi.ticketservice.dto.MembershipSubscribeRequest;
import com.tiketi.ticketservice.security.AuthUser;
import com.tiketi.ticketservice.security.JwtTokenParser;
import com.tiketi.ticketservice.service.MembershipService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memberships")
public class MembershipController {

    private final MembershipService membershipService;
    private final JwtTokenParser jwtTokenParser;

    public MembershipController(MembershipService membershipService, JwtTokenParser jwtTokenParser) {
        this.membershipService = membershipService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody MembershipSubscribeRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(membershipService.subscribe(user.userId(), request.artistId()));
    }

    @GetMapping("/my")
    public Map<String, Object> myMemberships(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return membershipService.getMyMemberships(user.userId());
    }

    @GetMapping("/my/{artistId}")
    public Map<String, Object> myMembershipForArtist(
        @PathVariable UUID artistId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return membershipService.getMyMembershipForArtist(user.userId(), artistId);
    }

    @GetMapping("/benefits/{artistId}")
    public Map<String, Object> benefits(
        @PathVariable UUID artistId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return membershipService.getUserBenefitsForArtist(user.userId(), artistId);
    }
}
