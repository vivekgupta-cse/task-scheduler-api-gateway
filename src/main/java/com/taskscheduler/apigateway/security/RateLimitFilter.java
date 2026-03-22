package com.taskscheduler.apigateway.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Reactive rate-limit filter for WebFlux / Gateway.
 * <p>
 * Runs as a WebFilter with high precedence so it executes before the
 * security filter chain. Uses an in-memory Caffeine cache with Bucket4j
 * buckets keyed by client IP.
 */
@Component
public class RateLimitFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // One bucket per IP; allow 20 requests per minute on auth endpoints
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/api/auth/")) {
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = remote != null && remote.getAddress() != null
                    ? remote.getAddress().getHostAddress()
                    : "unknown";

            Bucket bucket = buckets.get(ip, k ->
                    Bucket.builder()
                            .addLimit(Bandwidth.simple(20, Duration.ofMinutes(1)))
                            .build());

            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for IP={}", ip);
                var response = exchange.getResponse();
                response.setStatusCode(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
                response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                var msg = "{\"error\":\"Too many requests. Try again later.\"}";
                var buffer = response.bufferFactory().wrap(msg.getBytes(StandardCharsets.UTF_8));
                return response.writeWith(Mono.just(buffer));
            } else {
                log.debug("Rate limit allowance granted for IP={}", ip);
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run before most other filters (including security)
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
