package com.tiketi.gatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component
@Order(0)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long WINDOW_MS = 60_000L;
    private static final int RETRY_AFTER_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;
    private final SecretKey jwtKey;

    private final int authRpm;
    private final int queueRpm;
    private final int bookingRpm;
    private final int generalRpm;

    public RateLimitFilter(StringRedisTemplate redisTemplate,
                           RedisScript<Long> rateLimitScript,
                           @Value("${rate-limit.auth-rpm:20}") int authRpm,
                           @Value("${rate-limit.queue-rpm:60}") int queueRpm,
                           @Value("${rate-limit.booking-rpm:10}") int bookingRpm,
                           @Value("${rate-limit.general-rpm:100}") int generalRpm,
                           @Value("${JWT_SECRET:}") String jwtSecret) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.authRpm = authRpm;
        this.queueRpm = queueRpm;
        this.bookingRpm = bookingRpm;
        this.generalRpm = generalRpm;
        this.jwtKey = buildKey(jwtSecret);
    }

    private SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) return null;
        byte[] keyBytes;
        try {
            keyBytes = java.util.Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            log.warn("JWT_SECRET is not valid Base64, using raw bytes");
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // /auth/me is a read-only identity check called on every page navigation;
        // rate-limiting it causes "인증확인중" hangs during normal browsing.
        // /health and /actuator are internal endpoints.
        return path.equals("/api/v1/auth/me") || path.equals("/api/auth/me")
                || path.equals("/health") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // /auth/me is a read-only identity check called on every page navigation;
        // rate-limiting it causes "인증확인중" hangs during normal browsing.
        // Note: shouldNotFilter is NOT reliably called by Spring Cloud Gateway MVC,
        // so we must check here as well.
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
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String subject = extractSubjectFromJwt(token);
            if (subject != null) {
                return "user:" + subject;
            }
        }

        // Use remote address directly - more reliable than X-Forwarded-For
        // which can be spoofed by clients
        return "ip:" + request.getRemoteAddr();
    }

    private String extractSubjectFromJwt(String token) {
        if (jwtKey == null) return null;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            log.debug("Failed to verify/extract subject from JWT: {}", e.getMessage());
            return null;
        }
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
        // Only write operations (reserve, create) count as BOOKING;
        // read-only seat/reservation queries use GENERAL limit
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
