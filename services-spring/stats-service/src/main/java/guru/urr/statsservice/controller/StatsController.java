package guru.urr.statsservice.controller;

import guru.urr.common.security.JwtTokenParser;
import guru.urr.statsservice.service.DashboardStatsService;
import guru.urr.statsservice.service.EventStatsService;
import guru.urr.statsservice.service.OperationalStatsService;
import guru.urr.statsservice.service.UserStatsService;
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

    private final DashboardStatsService dashboardStatsService;
    private final EventStatsService eventStatsService;
    private final UserStatsService userStatsService;
    private final OperationalStatsService operationalStatsService;
    private final JwtTokenParser jwtTokenParser;

    public StatsController(DashboardStatsService dashboardStatsService,
                           EventStatsService eventStatsService,
                           UserStatsService userStatsService,
                           OperationalStatsService operationalStatsService,
                           JwtTokenParser jwtTokenParser) {
        this.dashboardStatsService = dashboardStatsService;
        this.eventStatsService = eventStatsService;
        this.userStatsService = userStatsService;
        this.operationalStatsService = operationalStatsService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return ok(dashboardStatsService.overview());
    }

    @GetMapping("/daily")
    public Map<String, Object> daily(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(dashboardStatsService.daily(days));
    }

    @GetMapping("/events")
    public Map<String, Object> events(
        HttpServletRequest request,
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(defaultValue = "revenue") String sortBy
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(eventStatsService.events(limit, sortBy));
    }

    @GetMapping("/events/{eventId}")
    public Map<String, Object> eventById(
        @PathVariable UUID eventId,
        HttpServletRequest request
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(eventStatsService.eventById(eventId));
    }

    @GetMapping("/payments")
    public Map<String, Object> payments(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return ok(operationalStatsService.payments());
    }

    @GetMapping("/revenue")
    public Map<String, Object> revenue(
        HttpServletRequest request,
        @RequestParam(defaultValue = "daily") String period,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(operationalStatsService.revenue(period, days));
    }

    @GetMapping("/users")
    public Map<String, Object> users(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(userStatsService.users(days));
    }

    @GetMapping("/hourly-traffic")
    public Map<String, Object> hourlyTraffic(
        HttpServletRequest request,
        @RequestParam(defaultValue = "7") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(operationalStatsService.hourlyTraffic(days));
    }

    @GetMapping("/conversion")
    public Map<String, Object> conversion(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(userStatsService.conversion(days));
    }

    @GetMapping("/cancellations")
    public Map<String, Object> cancellations(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(eventStatsService.cancellations(days));
    }

    @GetMapping("/realtime")
    public Map<String, Object> realtime(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return ok(operationalStatsService.realtime());
    }

    @GetMapping("/seat-preferences")
    public Map<String, Object> seatPreferences(
        HttpServletRequest request,
        @RequestParam(required = false) UUID eventId
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(eventStatsService.seatPreferences(eventId));
    }

    @GetMapping("/user-behavior")
    public Map<String, Object> userBehavior(
        HttpServletRequest request,
        @RequestParam(defaultValue = "30") int days
    ) {
        jwtTokenParser.requireAdmin(request);
        return ok(userStatsService.userBehavior(days));
    }

    @GetMapping("/performance")
    public Map<String, Object> performance(HttpServletRequest request) {
        jwtTokenParser.requireAdmin(request);
        return ok(operationalStatsService.performance());
    }

    private Map<String, Object> ok(Object data) {
        return Map.of("success", true, "data", data);
    }
}
