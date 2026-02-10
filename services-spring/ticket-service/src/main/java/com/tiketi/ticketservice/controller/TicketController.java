package com.tiketi.ticketservice.controller;

import com.tiketi.ticketservice.service.EventReadService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final EventReadService eventReadService;

    public TicketController(EventReadService eventReadService) {
        this.eventReadService = eventReadService;
    }

    @GetMapping("/event/{eventId}")
    public Map<String, Object> getTicketsByEvent(@PathVariable UUID eventId) {
        return eventReadService.getTicketsByEvent(eventId);
    }

    @GetMapping("/availability/{ticketTypeId}")
    public Map<String, Object> getAvailability(@PathVariable UUID ticketTypeId) {
        return eventReadService.getTicketAvailability(ticketTypeId);
    }
}
