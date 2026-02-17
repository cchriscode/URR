package guru.urr.gatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates JWT from Authorization header and injects X-User-Id, X-User-Email, X-User-Role
 * headers into the request for downstream services. This centralizes JWT validation at the
 * gateway so downstream services never need JWT_SECRET.
 *
 * Also strips any externally-provided X-User-* headers to prevent spoofing.
 */
@Component
@Order(-1)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    private final SecretKey jwtKey;

    public JwtAuthFilter(@Value("${JWT_SECRET:}") String jwtSecret) {
        this.jwtKey = buildKey(jwtSecret);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Always strip external X-User-* headers to prevent spoofing
        HttpServletRequest sanitized = new UserHeaderStrippingWrapper(request);

        String authHeader = sanitized.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ") && jwtKey != null) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                    .verifyWith(jwtKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

                String userId = claims.get("userId", String.class);
                String email = claims.get("email", String.class);
                String role = claims.get("role", String.class);

                if (userId != null && !userId.isBlank()) {
                    Map<String, String> injectedHeaders = new LinkedHashMap<>();
                    injectedHeaders.put(HEADER_USER_ID, userId);
                    if (email != null) injectedHeaders.put(HEADER_USER_EMAIL, email);
                    injectedHeaders.put(HEADER_USER_ROLE, role != null ? role : "user");

                    filterChain.doFilter(new UserHeaderInjectionWrapper(sanitized, injectedHeaders), response);
                    return;
                }
            } catch (Exception e) {
                log.debug("JWT validation failed: {}", e.getMessage());
            }
        }

        // No valid JWT — pass through without X-User-* headers
        filterChain.doFilter(sanitized, response);
    }

    private SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) return null;
        byte[] keyBytes;
        try {
            keyBytes = java.util.Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) return null;
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Strips X-User-Id, X-User-Email, X-User-Role from incoming requests
     * to prevent external header spoofing.
     */
    private static class UserHeaderStrippingWrapper extends HttpServletRequestWrapper {
        private static final List<String> STRIPPED = List.of(
            HEADER_USER_ID.toLowerCase(),
            HEADER_USER_EMAIL.toLowerCase(),
            HEADER_USER_ROLE.toLowerCase()
        );

        UserHeaderStrippingWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            if (isStripped(name)) return null;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isStripped(name)) return Collections.emptyEnumeration();
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(this::isStripped);
            return Collections.enumeration(names);
        }

        private boolean isStripped(String name) {
            return STRIPPED.contains(name.toLowerCase());
        }
    }

    /**
     * Injects X-User-* headers into the request after successful JWT validation.
     */
    private static class UserHeaderInjectionWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> injectedHeaders;

        UserHeaderInjectionWrapper(HttpServletRequest request, Map<String, String> injectedHeaders) {
            super(request);
            this.injectedHeaders = injectedHeaders;
        }

        @Override
        public String getHeader(String name) {
            String injected = findInjected(name);
            if (injected != null) return injected;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String injected = findInjected(name);
            if (injected != null) return Collections.enumeration(List.of(injected));
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new java.util.ArrayList<>(Collections.list(super.getHeaderNames()));
            for (String key : injectedHeaders.keySet()) {
                if (names.stream().noneMatch(n -> n.equalsIgnoreCase(key))) {
                    names.add(key);
                }
            }
            return Collections.enumeration(names);
        }

        private String findInjected(String name) {
            for (Map.Entry<String, String> entry : injectedHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
            }
            return null;
        }
    }
}
