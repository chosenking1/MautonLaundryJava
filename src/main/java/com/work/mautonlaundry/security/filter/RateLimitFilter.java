package com.work.mautonlaundry.security.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final Bandwidth AUTH_LIMIT = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
    private static final Bandwidth DISCOUNT_CHECK_LIMIT = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
    private static final Bandwidth DEFAULT_LIMIT = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));

    private static final long MAX_BUCKET_ENTRIES = 10000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        if (buckets.size() > MAX_BUCKET_ENTRIES) {
            cleanupOldEntries();
        }
        
        String path = request.getRequestURI();
        String clientKey = getClientKey(request);
        String bucketScope = resolveBucketScope(path);
        String bucketKey = bucketScope + ":" + clientKey;
        
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> Bucket.builder().addLimit(resolveBandwidth(path)).build());

        if (bucket.tryConsume(1)) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Please try again later.\"}");
        }
    }

    @Scheduled(fixedRate = 300000)
    public void cleanupOldEntries() {
        Iterator<Map.Entry<String, Bucket>> iterator = buckets.entrySet().iterator();
        int removed = 0;
        while (iterator.hasNext() && removed < 1000) {
            Map.Entry<String, Bucket> entry = iterator.next();
            if (entry.getValue().getAvailableTokens() >= 95) {
                iterator.remove();
                removed++;
            }
        }
    }

    private String getClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            int commaIndex = forwarded.indexOf(',');
            if (commaIndex > 0) {
                return forwarded.substring(0, commaIndex).trim();
            }
            return forwarded.trim();
        }
        return request.getRemoteAddr();
    }

    private Bandwidth resolveBandwidth(String path) {
        if (path.startsWith("/api/auth") || path.equals("/login") || path.equals("/register")) {
            return AUTH_LIMIT;
        }
        if (path.startsWith("/api/v1/discounts/check")) {
            return DISCOUNT_CHECK_LIMIT;
        }
        return DEFAULT_LIMIT;
    }

    private String resolveBucketScope(String path) {
        if (path.startsWith("/api/auth") || path.equals("/login") || path.equals("/register")) {
            return "auth";
        }
        if (path.startsWith("/api/v1/discounts/check")) {
            return "discount-check";
        }
        return "default";
    }
}
