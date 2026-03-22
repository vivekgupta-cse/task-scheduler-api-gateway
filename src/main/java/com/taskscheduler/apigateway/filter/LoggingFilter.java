package com.taskscheduler.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. Capture request details 📥
        var request = exchange.getRequest();
        var path = request.getPath().value();
        var method = request.getMethod();
        var headers = request.getHeaders();
        var body = request.getBody();

        logger.info("Gateway Request: method=%s, url=%s, headers=%s, body=%s".formatted(method, path, headers, body));

        // 2. Execute the chain and hook into the response 📤
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            var responseStatus = exchange.getResponse().getStatusCode();
            logger.info("Gateway Response for {} {}: {}", method, path, responseStatus);
        }));
    }

    @Override
    public int getOrder() {
        // Run this filter as early as possible
        return Ordered.HIGHEST_PRECEDENCE;
    }
}