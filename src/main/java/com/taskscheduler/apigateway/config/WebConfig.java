package com.taskscheduler.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFlux configuration.
 * <p>
 * Provides a WebFilter that redirects requests ending with a trailing slash
 * (e.g. /api/tasks/) to the canonical form (/api/tasks) while preserving
 * the query string.
 */
@Configuration
public class WebConfig {

    @Bean
    public WebFilter trailingSlashRedirectFilter() {
        return new WebFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
                String uri = exchange.getRequest().getPath().value();

                if (uri.startsWith("/api/") && uri.endsWith("/")) {
                    // Normalize the path by removing the trailing slash and continue the chain.
                    // Returning an HTTP redirect forces the client to re-issue the request and may
                    // drop or change headers (Authorization, cookies) — for an API gateway we
                    // generally want to preserve the original request and route it directly.
                    String cleanUri = uri.substring(0, uri.length() - 1);
                    String query = exchange.getRequest().getURI().getQuery();

                    // Use the request builder to set the new path and continue the filter chain
                    // so downstream routing sees the canonical path without requiring a client
                    // redirect.
                    org.springframework.http.server.reactive.ServerHttpRequest mutated =
                            exchange.getRequest().mutate()
                                    .path(cleanUri + (query != null ? "?" + query : ""))
                                    .build();

                    return chain.filter(exchange.mutate().request(mutated).build());
                }

                return chain.filter(exchange);
            }
        };
    }
}

