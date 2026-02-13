package com.tiketi.queueservice.controller;

import com.tiketi.queueservice.shared.security.AuthUser;
import com.tiketi.queueservice.shared.security.JwtTokenParser;
import com.tiketi.queueservice.service.QueueService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return queueService.check(eventId, user.userId());
    }

    @GetMapping("/status/{eventId}")
    public Map<String, Object> status(
        @PathVariable UUID eventId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return queueService.status(eventId, user.userId());
    }

    @PostMapping("/heartbeat/{eventId}")
    public Map<String, Object> heartbeat(
        @PathVariable UUID eventId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return queueService.heartbeat(eventId, user.userId());
    }

    @PostMapping("/leave/{eventId}")
    public Map<String, Object> leave(
        @PathVariable UUID eventId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return queueService.leave(eventId, user.userId());
    }

    @GetMapping("/admin/{eventId}")
    public Map<String, Object> admin(
        @PathVariable UUID eventId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return queueService.admin(eventId);
    }

    @PostMapping("/admin/clear/{eventId}")
    public Map<String, Object> clear(
        @PathVariable UUID eventId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        jwtTokenParser.requireAdmin(authorization);
        return queueService.clear(eventId);
    }
}
