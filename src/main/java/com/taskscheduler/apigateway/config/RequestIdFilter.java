package com.taskscheduler.apigateway.config;

import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive request-id filter for WebFlux / Gateway.
 * <p>
 * Looks for an incoming X-Request-Id header and uses it if present; otherwise
 * generates a new UUID. The value is placed into MDC under key "requestId"
 * and also added to the response headers. MDC is cleaned up when the reactive
 * chain terminates.
 */
@Component
public class RequestIdFilter implements WebFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID = "requestId";

    @Override
    public int getOrder() {
        // Run early so downstream logs can pick up the request id
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }

        // Put into MDC for the duration of the reactive processing
        MDC.put(REQUEST_ID, id);

        // Mutate the incoming request to include the X-Request-Id header so downstream
        // services receive it when the gateway proxies the request.
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, id)
                .build();

        // Also set it on the response headers so clients can see the id
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, id);

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .doFinally(__ -> MDC.remove(REQUEST_ID));
    }
}
