package com.tiketi.statsservice.controller;

import com.tiketi.statsservice.security.JwtTokenParser;
import com.tiketi.statsservice.service.StatsQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsQueryService statsQueryService;
    private final JwtTokenParser jwtTokenParser;

    public StatsController(StatsQueryService statsQueryService, JwtTokenParser jwtTokenParser) {
        this.statsQueryService = statsQueryService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.overview());
    }

    @GetMapping("/daily")
    public Map<String, Object> daily(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.daily(days));
    }

    @GetMapping("/events")
    public Map<String, Object> events(
        HttpServletRequest request,
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(defaultValue = "revenue") String sortBy
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.events(limit, sortBy));
    }

    @GetMapping("/events/{eventId}")
    public Map<String, Object> eventById(
        @PathVariable UUID eventId,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.eventById(eventId));
    }

    @GetMapping("/payments")
    public Map<String, Object> payments(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.payments());
    }

    @GetMapping("/revenue")
    public Map<String, Object> revenue(
        HttpServletRequest request,
        @RequestParam(defaultValue = "daily") String period,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.revenue(period, days));
    }

    @GetMapping("/users")
    public Map<String, Object> users(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.users(days));
    }

    @GetMapping("/hourly-traffic")
    public Map<String, Object> hourlyTraffic(
        HttpServletRequest request,
        @RequestParam(defaultValue = "7") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.hourlyTraffic(days));
    }

    @GetMapping("/conversion")
    public Map<String, Object> conversion(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.conversion(days));
    }

    @GetMapping("/cancellations")
    public Map<String, Object> cancellations(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.cancellations(days));
    }

    @GetMapping("/realtime")
    public Map<String, Object> realtime(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.realtime());
    }

    @GetMapping("/seat-preferences")
    public Map<String, Object> seatPreferences(
        HttpServletRequest request,
        @RequestParam(required = false) UUID eventId
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.seatPreferences(eventId));
    }

    @GetMapping("/user-behavior")
    public Map<String, Object> userBehavior(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.userBehavior(days));
    }

    @GetMapping("/performance")
    public Map<String, Object> performance(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return ok(statsQueryService.performance());
    }

    private Map<String, Object> ok(Object data) {
        return Map.of("success", true, "data", data);
    }
}
