package com.tiketi.catalogservice.domain.event.controller;

import com.tiketi.catalogservice.domain.event.service.EventReadService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventReadService eventReadService;

    public EventController(EventReadService eventReadService) {
        this.eventReadService = eventReadService;
    }

    @GetMapping
    public Map<String, Object> getEvents(
        @RequestParam(required = false) String status,
        @RequestParam(name = "q", required = false) String searchQuery,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int limit
    ) {
        return eventReadService.listEvents(status, searchQuery, page, limit);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getEvent(@PathVariable UUID id) {
        return eventReadService.getEventDetail(id);
    }
}
