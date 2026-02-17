package guru.urr.ticketservice.domain.membership.controller;

import guru.urr.ticketservice.domain.membership.dto.MembershipSubscribeRequest;
import guru.urr.ticketservice.shared.security.AuthUser;
import guru.urr.ticketservice.shared.security.JwtTokenParser;
import guru.urr.ticketservice.domain.membership.service.MembershipService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        HttpServletRequest request,
        @Valid @RequestBody MembershipSubscribeRequest body
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(membershipService.subscribe(user.userId(), body.artistId()));
    }

    @GetMapping("/my")
    public Map<String, Object> myMemberships(HttpServletRequest request) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return membershipService.getMyMemberships(user.userId());
    }

    @GetMapping("/my/{artistId}")
    public Map<String, Object> myMembershipForArtist(
        @PathVariable UUID artistId,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return membershipService.getMyMembershipForArtist(user.userId(), artistId);
    }

    @GetMapping("/benefits/{artistId}")
    public Map<String, Object> benefits(
        @PathVariable UUID artistId,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return membershipService.getUserBenefitsForArtist(user.userId(), artistId);
    }
}
