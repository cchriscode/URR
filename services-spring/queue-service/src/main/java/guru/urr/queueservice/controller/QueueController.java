package guru.urr.queueservice.controller;

import guru.urr.queueservice.shared.security.AuthUser;
import guru.urr.queueservice.shared.security.JwtTokenParser;
import guru.urr.queueservice.service.QueueService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private final QueueService queueService;
    private final JwtTokenParser jwtTokenParser;

    public QueueController(QueueService queueService, JwtTokenParser jwtTokenParser) {
        this.queueService = queueService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @PostMapping("/check/{eventId}")
    public Map<String, Object> check(
        @PathVariable UUID eventId,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return queueService.check(eventId, user.userId());
    }

    @GetMapping("/status/{eventId}")
    public Map<String, Object> status(
        @PathVariable UUID eventId,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return queueService.status(eventId, user.userId());
    }

    @PostMapping("/heartbeat/{eventId}")
    public Map<String, Object> heartbeat(
        @PathVariable UUID eventId,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return queueService.heartbeat(eventId, user.userId());
    }

    @PostMapping("/leave/{eventId}")
    public Map<String, Object> leave(
        @PathVariable UUID eventId,
        HttpServletRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(request);
        return queueService.leave(eventId, user.userId());
    }

    @GetMapping("/admin/{eventId}")
    public Map<String, Object> admin(
        @PathVariable UUID eventId,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireAdmin(request);
        return queueService.admin(eventId);
    }

    @PostMapping("/admin/clear/{eventId}")
    public Map<String, Object> clear(
        @PathVariable UUID eventId,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireAdmin(request);
        return queueService.clear(eventId);
    }
}
