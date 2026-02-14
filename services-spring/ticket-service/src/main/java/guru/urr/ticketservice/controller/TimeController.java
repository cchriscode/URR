package guru.urr.ticketservice.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/time")
public class TimeController {

    @GetMapping
    public Map<String, Object> getServerTime() {
        return Map.of("time", Instant.now().toString());
    }
}
