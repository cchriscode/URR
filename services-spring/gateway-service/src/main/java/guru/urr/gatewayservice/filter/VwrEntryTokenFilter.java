package guru.urr.gatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

// Entry Token is expiration-based one-time use. Reservation duplication is prevented by idempotencyKey.
// For strict one-time enforcement, consider tracking JWT jti claims in Redis SET with TTL.
@Component
@Order(1)
public class VwrEntryTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VwrEntryTokenFilter.class);

    private static final String HEADER_NAME = "x-queue-entry-token";
    private static final String CF_VERIFIED_HEADER = "X-CloudFront-Verified";
    private static final int HMAC_MIN_KEY_LENGTH = 32;
    private static final Set<String> PROTECTED_METHODS = Set.of("GET", "POST", "PUT", "PATCH");

    private final SecretKey signingKey;
    private final String cloudFrontSecret;

    public VwrEntryTokenFilter(
            @Value("${queue.entry-token.secret}") String secret,
            @Value("${cloudfront.secret:}") String cloudFrontSecret) {
        this.signingKey = buildSigningKey(secret);
        this.cloudFrontSecret = (cloudFrontSecret != null && !cloudFrontSecret.isBlank())
                ? cloudFrontSecret : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase();
        if (!PROTECTED_METHODS.contains(method)) {
            return true;
        }

        String path = request.getRequestURI();
        return !isProtectedPath(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // CloudFront bypass: Lambda@Edge already verified the token at CDN edge
        if (cloudFrontSecret != null) {
            String cfHeader = request.getHeader(CF_VERIFIED_HEADER);
            if (cfHeader != null && MessageDigest.isEqual(
                    cloudFrontSecret.getBytes(StandardCharsets.UTF_8),
                    cfHeader.getBytes(StandardCharsets.UTF_8))) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String token = request.getHeader(HEADER_NAME);

        if (token == null || token.isBlank()) {
            log.warn("Missing VWR entry token for {} {}", request.getMethod(), request.getRequestURI());
            sendForbiddenResponse(response);
            return;
        }

        try {
            Claims vwrClaims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Validate userId binding: VWR token uid must match Auth JWT subject
            String vwrUserId = vwrClaims.get("uid", String.class);
            if (vwrUserId != null) {
                String authUserId = extractAuthUserId(request);
                if (authUserId != null && !vwrUserId.equals(authUserId)) {
                    log.warn("VWR token userId mismatch: token uid={} auth sub={} path={}",
                            vwrUserId, authUserId, request.getRequestURI());
                    sendForbiddenResponse(response);
                    return;
                }
            }

            // Forward eventId from VWR token for backend validation
            String vwrEventId = vwrClaims.getSubject();
            if (vwrEventId != null) {
                request.setAttribute("vwr.eventId", vwrEventId);
            }

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("Invalid VWR entry token for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
            sendForbiddenResponse(response);
        }
    }

    private boolean isProtectedPath(String path) {
        if (path.startsWith("/api/seats/")) {
            return true;
        }
        if (path.startsWith("/api/reservations")) {
            // Allow reading own reservations without queue token
            return !path.startsWith("/api/reservations/my");
        }
        return false;
    }

    private void sendForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Queue entry token required\",\"redirectTo\":\"/queue\"}");
        response.getWriter().flush();
    }

    private String extractAuthUserId(HttpServletRequest request) {
        // Use X-User-Id injected by JwtAuthFilter (no JWT parsing needed)
        String userId = request.getHeader(JwtAuthFilter.HEADER_USER_ID);
        return (userId != null && !userId.isBlank()) ? userId : null;
    }

    private static SecretKey buildSigningKey(String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < HMAC_MIN_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "QUEUE_ENTRY_TOKEN_SECRET must be at least " + HMAC_MIN_KEY_LENGTH + " bytes");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
