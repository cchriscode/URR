package com.tiketi.ticketservice.controller;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

    private final ObjectProvider<PrometheusMeterRegistry> registry;

    public OpsController(ObjectProvider<PrometheusMeterRegistry> registry) {
        this.registry = registry;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "ticket-service");
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        PrometheusMeterRegistry meterRegistry = registry.getIfAvailable();
        return meterRegistry != null ? meterRegistry.scrape() : "";
    }
}
