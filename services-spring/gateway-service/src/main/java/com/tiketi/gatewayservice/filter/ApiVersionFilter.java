package com.tiketi.gatewayservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Strips the /v1/ version segment from API paths so that downstream
 * services receive the unversioned path they already handle.
 * e.g. /api/v1/events/123 → /api/events/123
 */
@Component
@Order(-10)
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final String V1_PREFIX = "/api/v1/";
    private static final String API_PREFIX = "/api/";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (uri.startsWith(V1_PREFIX)) {
            String rewritten = API_PREFIX + uri.substring(V1_PREFIX.length());
            filterChain.doFilter(new RewrittenPathRequest(request, rewritten), response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private static class RewrittenPathRequest extends HttpServletRequestWrapper {
        private final String newUri;

        RewrittenPathRequest(HttpServletRequest request, String newUri) {
            super(request);
            this.newUri = newUri;
        }

        @Override
        public String getRequestURI() {
            return newUri;
        }

        @Override
        public String getServletPath() {
            return newUri;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            url.append(getScheme()).append("://").append(getServerName());
            int port = getServerPort();
            if (port != 80 && port != 443) {
                url.append(':').append(port);
            }
            url.append(newUri);
            return url;
        }
    }
}
