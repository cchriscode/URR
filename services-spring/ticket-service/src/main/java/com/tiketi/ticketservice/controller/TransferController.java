package com.tiketi.ticketservice.controller;

import com.tiketi.ticketservice.security.AuthUser;
import com.tiketi.ticketservice.security.JwtTokenParser;
import com.tiketi.ticketservice.service.TransferService;
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
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;
    private final JwtTokenParser jwtTokenParser;

    public TransferController(TransferService transferService, JwtTokenParser jwtTokenParser) {
        this.transferService = transferService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @PostMapping
    public Map<String, Object> createListing(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody Map<String, String> body
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        UUID reservationId = UUID.fromString(body.get("reservationId"));
        return transferService.createListing(user.userId(), reservationId);
    }

    @GetMapping
    public Map<String, Object> listAvailable(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(required = false) String artistId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int limit
    ) {
        jwtTokenParser.requireUser(authorization);
        UUID aid = artistId != null && !artistId.isBlank() ? UUID.fromString(artistId) : null;
        return transferService.getAvailableTransfers(aid, page, limit);
    }

    @GetMapping("/my")
    public Map<String, Object> myListings(
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return transferService.getMyListings(user.userId());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        jwtTokenParser.requireUser(authorization);
        return transferService.getTransferDetail(id);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(
        @PathVariable UUID id,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return transferService.cancelListing(user.userId(), id);
    }
}
