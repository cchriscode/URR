package guru.urr.gatewayservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long WINDOW_MS = 60_000L;
    private static final int RETRY_AFTER_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    private final int authRpm;
    private final int queueRpm;
    private final int bookingRpm;
    private final int generalRpm;

    public RateLimitFilter(StringRedisTemplate redisTemplate,
                           RedisScript<Long> rateLimitScript,
                           @Value("${rate-limit.auth-rpm:20}") int authRpm,
                           @Value("${rate-limit.queue-rpm:60}") int queueRpm,
                           @Value("${rate-limit.booking-rpm:10}") int bookingRpm,
                           @Value("${rate-limit.general-rpm:100}") int generalRpm) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.authRpm = authRpm;
        this.queueRpm = queueRpm;
        this.bookingRpm = bookingRpm;
        this.generalRpm = generalRpm;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/me") || path.equals("/api/auth/me")
                || path.equals("/health") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.equals("/api/v1/auth/me") || path.equals("/api/auth/me")
                || path.equals("/health") || path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = resolveClientId(request);
        RateCategory category = resolveCategory(path);
        int limit = getLimitForCategory(category);

        String redisKey = "rate:" + category.name().toLowerCase() + ":" + clientId;
        long now = System.currentTimeMillis();
        String requestId = now + ":" + UUID.randomUUID();

        try {
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(now),
                    String.valueOf(WINDOW_MS),
                    String.valueOf(limit),
                    requestId
            );

            if (result == null || result == 0L) {
                log.warn("Rate limit exceeded for client={} category={} path={}",
                        clientId, category, path);
                sendRateLimitResponse(response);
                return;
            }
        } catch (Exception e) {
            log.warn("Rate limit check failed (fail-open), allowing request: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientId(HttpServletRequest request) {
        // Use X-User-Id injected by JwtAuthFilter (no JWT parsing needed here)
        String userId = request.getHeader(JwtAuthFilter.HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            return "user:" + userId;
        }

        return "ip:" + request.getRemoteAddr();
    }

    private RateCategory resolveCategory(String path) {
        if (path.startsWith("/api/v1/auth/") || path.startsWith("/api/auth/")
                || path.equals("/api/v1/auth") || path.equals("/api/auth")) {
            return RateCategory.AUTH;
        }
        if (path.startsWith("/api/v1/queue/") || path.startsWith("/api/queue/")
                || path.equals("/api/v1/queue") || path.equals("/api/queue")) {
            return RateCategory.QUEUE;
        }
        if (path.equals("/api/v1/seats/reserve") || path.equals("/api/seats/reserve")
                || path.equals("/api/v1/reservations") || path.equals("/api/reservations")
                || path.matches("/api/(v1/)?reservations/[^/]+/cancel")) {
            return RateCategory.BOOKING;
        }
        return RateCategory.GENERAL;
    }

    private int getLimitForCategory(RateCategory category) {
        return switch (category) {
            case AUTH -> authRpm;
            case QUEUE -> queueRpm;
            case BOOKING -> bookingRpm;
            case GENERAL -> generalRpm;
        };
    }

    private void sendRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"Rate limit exceeded\",\"retryAfter\":" + RETRY_AFTER_SECONDS + "}"
        );
        response.getWriter().flush();
    }

    private enum RateCategory {
        AUTH,
        QUEUE,
        BOOKING,
        GENERAL
    }
}
