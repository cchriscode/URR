package com.tiketi.gatewayservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-1)
public class CookieAuthFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // If Authorization header already present, skip cookie extraction
        if (request.getHeader("Authorization") != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract access_token from cookie
        String token = extractCookieValue(request, ACCESS_TOKEN_COOKIE);
        if (token != null && !token.isBlank()) {
            // Wrap request to add Authorization header
            HttpServletRequest wrappedRequest = new AuthHeaderRequestWrapper(request, "Bearer " + token);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private String extractCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static class AuthHeaderRequestWrapper extends HttpServletRequestWrapper {
        private final String authHeaderValue;

        AuthHeaderRequestWrapper(HttpServletRequest request, String authHeaderValue) {
            super(request);
            this.authHeaderValue = authHeaderValue;
        }

        @Override
        public String getHeader(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return authHeaderValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(authHeaderValue));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (!names.stream().anyMatch(n -> "Authorization".equalsIgnoreCase(n))) {
                names.add("Authorization");
            }
            return Collections.enumeration(names);
        }
    }
}
