package com.dojostay.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-local, per-client rate limiter. Not a replacement for a real
 * gateway, but sufficient to turn away obvious flooding attacks on
 * authentication and write endpoints while the app runs behind a reverse
 * proxy.
 *
 * <p>Algorithm: fixed window per (client, bucket) using a {@link ConcurrentHashMap}
 * keyed on client ip + endpoint bucket. Counters reset whenever the wall-clock
 * enters a new window. Two buckets:
 *
 * <ul>
 *   <li>{@code auth} — {@code POST /api/auth/login} at
 *       {@code dojostay.security.rate-limit.auth-per-minute} rps/minute. Limits
 *       credential brute-force before it reaches the lockout counter.</li>
 *   <li>{@code write} — all non-{@code GET} requests at
 *       {@code dojostay.security.rate-limit.write-per-minute} rps/minute.</li>
 * </ul>
 *
 * Over-limit requests are rejected with 429 and a JSON body using the standard
 * {@link ApiResponse} error envelope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final long WINDOW_MS = 60_000L;

    private final int authPerMinute;
    private final int writePerMinute;
    private final ObjectMapper objectMapper;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${dojostay.security.rate-limit.auth-per-minute:30}") int authPerMinute,
            @Value("${dojostay.security.rate-limit.write-per-minute:240}") int writePerMinute,
            ObjectMapper objectMapper) {
        this.authPerMinute = authPerMinute;
        this.writePerMinute = writePerMinute;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String ip = clientIp(request);

        // Auth bucket only applies to the login endpoint.
        if ("POST".equalsIgnoreCase(method) && path != null && path.endsWith("/api/auth/login")) {
            if (!allow("auth:" + ip, authPerMinute)) {
                reject(response, "Too many authentication attempts, slow down.");
                return;
            }
        } else if (!"GET".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method)) {
            if (!allow("write:" + ip, writePerMinute)) {
                reject(response, "Too many write requests, slow down.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean allow(String key, int limit) {
        long now = System.currentTimeMillis();
        long windowStart = now - (now % WINDOW_MS);
        Counter c = counters.computeIfAbsent(key, k -> new Counter(windowStart));
        synchronized (c) {
            if (c.windowStart != windowStart) {
                c.windowStart = windowStart;
                c.count.set(0);
            }
            long count = c.count.incrementAndGet();
            return count <= limit;
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        ApiError err = ApiError.of("RATE_LIMITED", message);
        ApiResponse<?> body = ApiResponse.failure(err);
        objectMapper.writeValue(response.getOutputStream(), body);
        log.warn("[rate-limit] 429 — {}", message);
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return req.getRemoteAddr();
    }

    private static final class Counter {
        volatile long windowStart;
        final AtomicLong count = new AtomicLong(0);
        Counter(long windowStart) { this.windowStart = windowStart; }
    }
}
