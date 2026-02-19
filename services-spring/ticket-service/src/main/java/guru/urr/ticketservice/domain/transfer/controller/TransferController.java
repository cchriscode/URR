package guru.urr.ticketservice.domain.transfer.controller;

import guru.urr.common.security.AuthUser;
import guru.urr.common.security.JwtTokenParser;
import guru.urr.ticketservice.domain.transfer.service.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        HttpServletRequest request,
        @RequestBody Map<String, String> body
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        UUID reservationId = UUID.fromString(body.get("reservationId"));
        return transferService.createListing(user.userId(), reservationId);
    }

    @GetMapping
    public Map<String, Object> listAvailable(
        HttpServletRequest request,
        @RequestParam(required = false) String artistId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int limit
    ) {
        jwtTokenParser.requireUser(request);
        UUID aid = artistId != null && !artistId.isBlank() ? UUID.fromString(artistId) : null;
        return transferService.getAvailableTransfers(aid, page, limit);
    }

    @GetMapping("/my")
    public Map<String, Object> myListings(HttpServletRequest request) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return transferService.getMyListings(user.userId());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireUser(request);
        return transferService.getTransferDetail(id);
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancel(
        @PathVariable UUID id,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return transferService.cancelListing(user.userId(), id);
    }
}
