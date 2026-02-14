package guru.urr.ticketservice.internal.controller;

import guru.urr.ticketservice.shared.security.InternalTokenValidator;
import guru.urr.ticketservice.scheduling.MaintenanceService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private final MaintenanceService maintenanceService;
    private final InternalTokenValidator internalTokenValidator;

    public InternalController(MaintenanceService maintenanceService, InternalTokenValidator internalTokenValidator) {
        this.maintenanceService = maintenanceService;
        this.internalTokenValidator = internalTokenValidator;
    }

    @PostMapping("/reschedule-event-status")
    public Map<String, Object> reschedule(@RequestHeader(value = "Authorization", required = false) String authorization) {
        internalTokenValidator.requireValidToken(authorization);
        maintenanceService.forceStatusReschedule();
        return Map.of("ok", true, "message", "Event status update executed");
    }
}
