package org.newsanalyzer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global, in-memory rate limiter for POST /api/articles.
 *
 * Story ES-1.3 makes this endpoint trigger a real reasoning-service call for
 * the first time — this protects against the resulting LLM-adjacent cost
 * exposure. Global (not per-IP): at MVP's manual/low volume, the actual risk
 * is aggregate request volume, not any single bad actor.
 *
 * Mirrors CongressApiClient's AtomicInteger/AtomicLong windowing mechanism,
 * but with reject-immediately semantics (429) rather than sleep-and-retry —
 * the right behavior for an inbound limiter protecting an endpoint, as
 * opposed to CongressApiClient's outbound self-throttle protecting a
 * third-party API's rate limit.
 */
@Component
public class ArticleIngestionRateLimiter {

    private static final long WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    private final int maxRequestsPerWindow;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

    public ArticleIngestionRateLimiter(
            @Value("${articles.rate-limit.max-per-minute:20}") int maxRequestsPerWindow) {
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    /**
     * @return true if the request is allowed, false if the rate limit has been exceeded
     */
    public boolean tryAcquire() {
        long now = System.currentTimeMillis();
        long windowStartTime = windowStart.get();

        if (now - windowStartTime >= WINDOW_MILLIS) {
            windowStart.set(now);
            requestCount.set(0);
        }

        return requestCount.incrementAndGet() <= maxRequestsPerWindow;
    }
}
