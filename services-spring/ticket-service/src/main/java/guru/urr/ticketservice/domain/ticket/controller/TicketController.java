package guru.urr.ticketservice.domain.ticket.controller;

import guru.urr.ticketservice.shared.service.CatalogReadService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final CatalogReadService catalogReadService;

    public TicketController(CatalogReadService catalogReadService) {
        this.catalogReadService = catalogReadService;
    }

    @GetMapping("/event/{eventId}")
    public Map<String, Object> getTicketsByEvent(@PathVariable UUID eventId) {
        return catalogReadService.getTicketsByEvent(eventId);
    }

    @GetMapping("/availability/{ticketTypeId}")
    public Map<String, Object> getAvailability(@PathVariable UUID ticketTypeId) {
        return catalogReadService.getTicketAvailability(ticketTypeId);
    }
}
