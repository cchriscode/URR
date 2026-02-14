package guru.urr.gatewayservice.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class VwrEntryTokenFilterTest {

    private static final String SECRET = "test-queue-entry-token-secret-minimum-32-chars-long";
    private static final String CF_SECRET = "my-cloudfront-shared-secret-value";

    @Mock private FilterChain filterChain;

    private VwrEntryTokenFilter filter;

    @BeforeEach
    void setUp() {
        filter = new VwrEntryTokenFilter(SECRET, "", CF_SECRET);
    }

    @Test
    void postSeatsReserve_withoutToken_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/seats/reserve");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("Queue entry token required"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void postReservationsCreate_withoutToken_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/reservations/create");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("Queue entry token required"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void postSeatsReserve_withValidJwt_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/seats/reserve");
        request.addHeader("x-queue-entry-token", generateValidToken());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void getSeats_skipsFilter_notProtectedMethod() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/seats/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void getEvents_skipsFilter_notProtectedPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events/123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    // --- CloudFront bypass tests ---

    @Test
    void cloudFrontBypass_validSecret_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/seats/reserve");
        request.addHeader("X-CloudFront-Verified", CF_SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void cloudFrontBypass_wrongSecret_fallsThroughToJwtCheck_returns403() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/seats/reserve");
        request.addHeader("X-CloudFront-Verified", "wrong-secret");
        // No JWT token provided, so JWT check also fails
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Queue entry token required"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void cloudFrontBypass_emptyCloudFrontSecret_jwtCheckProceedsNormally() throws Exception {
        // Create filter with empty cloudfront secret (disabled)
        VwrEntryTokenFilter filterNoCloudFront = new VwrEntryTokenFilter(SECRET, "", "");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/seats/reserve");
        request.addHeader("X-CloudFront-Verified", "some-value");
        // Without a valid JWT, this should be rejected
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterNoCloudFront.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("Queue entry token required"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void cloudFrontBypass_emptyCloudFrontSecret_validJwt_passesThrough() throws Exception {
        // Create filter with empty cloudfront secret (disabled)
        VwrEntryTokenFilter filterNoCloudFront = new VwrEntryTokenFilter(SECRET, "", "");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/seats/reserve");
        request.addHeader("X-CloudFront-Verified", "some-value");
        request.addHeader("x-queue-entry-token", generateValidToken());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterNoCloudFront.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    private String generateValidToken() {
        SecretKey key = buildSigningKey(SECRET);
        long nowMs = System.currentTimeMillis();
        return Jwts.builder()
                .subject("test-event-id")
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + 600_000))
                .signWith(key)
                .compact();
    }

    private static SecretKey buildSigningKey(String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            byte[] padded = new byte[32];
            Arrays.fill(padded, (byte) 0);
            System.arraycopy(secretBytes, 0, padded, 0, secretBytes.length);
            secretBytes = padded;
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
