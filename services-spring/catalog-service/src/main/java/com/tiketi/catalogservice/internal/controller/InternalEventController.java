package com.tiketi.catalogservice.internal.controller;

import com.tiketi.catalogservice.domain.event.service.EventReadService;
import com.tiketi.catalogservice.shared.security.InternalTokenValidator;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/events")
public class InternalEventController {

    private final EventReadService eventReadService;
    private final InternalTokenValidator internalTokenValidator;

    public InternalEventController(EventReadService eventReadService,
                                    InternalTokenValidator internalTokenValidator) {
        this.eventReadService = eventReadService;
        this.internalTokenValidator = internalTokenValidator;
    }

    @GetMapping("/{eventId}/queue-info")
    public Map<String, Object> getQueueInfo(
            @PathVariable UUID eventId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        return eventReadService.getEventQueueInfo(eventId);
    }
}
