package guru.urr.gatewayservice.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisScript<Long> rateLimitScript;
    @Mock private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redisTemplate, rateLimitScript, 20, 60, 10, 100, "dGVzdC1zZWNyZXQtMTIzNDU2Nzg5MDEyMzQ1Njc4OTA=");
    }

    @Test
    void request_allowed_passesThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redisTemplate.execute(eq(rateLimitScript), anyList(), any(String.class),
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(1L);

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void request_denied_returns429() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redisTemplate.execute(eq(rateLimitScript), anyList(), any(String.class),
                any(String.class), any(String.class), any(String.class)))
                .thenReturn(0L);

        filter.doFilter(request, response, filterChain);

        assertEquals(429, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("Rate limit exceeded"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void request_redisException_failOpen() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/events");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(redisTemplate.execute(eq(rateLimitScript), anyList(), any(String.class),
                any(String.class), any(String.class), any(String.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }
}
