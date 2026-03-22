package com.taskscheduler.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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

                if (uri.length() > 1 && uri.endsWith("/") && uri.startsWith("/api/")) {
                    String cleanUri = uri.substring(0, uri.length() - 1);
                    String query = exchange.getRequest().getURI().getQuery();
                    String redirectUrl = (query != null) ? cleanUri + "?" + query : cleanUri;
                    exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.PERMANENT_REDIRECT);
                    exchange.getResponse().getHeaders().setLocation(java.net.URI.create(redirectUrl));
                    return exchange.getResponse().setComplete();
                }

                return chain.filter(exchange);
            }
        };
    }
}

