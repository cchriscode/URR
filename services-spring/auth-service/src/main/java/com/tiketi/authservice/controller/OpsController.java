package com.tiketi.authservice.controller;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpsController {

    private final ObjectProvider<PrometheusMeterRegistry> prometheusMeterRegistry;

    public OpsController(ObjectProvider<PrometheusMeterRegistry> prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "auth-service");
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String metrics() {
        PrometheusMeterRegistry registry = prometheusMeterRegistry.getIfAvailable();
        return registry != null ? registry.scrape() : "";
    }
}
