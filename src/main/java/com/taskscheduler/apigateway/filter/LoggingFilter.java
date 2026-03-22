package com.taskscheduler.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    // Maximum bytes to buffer for logging (configurable if needed)
    private static final int MAX_LOG_BYTES = 64 * 1024; // 64KB

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().toString();
        String path = request.getPath().value();
        HttpHeaders headers = request.getHeaders();

        // Only attempt to aggregate textual payloads
        MediaType contentType = headers.getContentType();
        boolean isTextLike = contentType == null
                || MediaType.APPLICATION_JSON.includes(contentType)
                || (contentType.getType() != null && contentType.getType().equalsIgnoreCase("text"))
                || contentType.getSubtype().toLowerCase().contains("json")
                || contentType.getSubtype().toLowerCase().contains("xml");

        boolean mayHaveBody = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);

        if (!mayHaveBody || !isTextLike) {
            logger.info("Gateway Request: method={}, url={}, headers={}", method, path, headers);
            return chain.filter(exchange).doFinally(signal -> {
                var status = exchange.getResponse().getStatusCode();
                logger.info("Gateway Response for {} {}: {}", method, path, status);
            });
        }

        return DataBufferUtils.join(request.getBody())
                .defaultIfEmpty(new DefaultDataBufferFactory().wrap(new byte[0]))
                .flatMap(joined -> {
                    try {
                        int len = joined.readableByteCount();
                        if (len > MAX_LOG_BYTES) {
                            // Too large: log metadata and first N bytes
                            byte[] prefix = new byte[MAX_LOG_BYTES];
                            joined.read(prefix);
                            String prefixStr = new String(prefix, StandardCharsets.UTF_8);
                            logger.info("Gateway Request: method={}, url={}, headers={}, body(truncated)={}... (originalBytes={})",
                                    method, path, headers, prefixStr, len);

                            // Provide an empty body to downstream to avoid re-streaming complexity here
                            Flux<DataBuffer> cachedFlux = Flux.defer(() -> Mono.just(new DefaultDataBufferFactory().wrap(new byte[0])));

                            ServerHttpRequest decorated = new ServerHttpRequestDecorator(request) {
                                @Override
                                public Flux<DataBuffer> getBody() {
                                    return cachedFlux;
                                }
                            };

                            return chain.filter(exchange.mutate().request(decorated).build())
                                    .doFinally(signal -> logger.info("Gateway Response for {} {}: {}", method, path, exchange.getResponse().getStatusCode()));
                        } else {
                            byte[] bytes = new byte[len];
                            joined.read(bytes);
                            String bodyString = new String(bytes, StandardCharsets.UTF_8);
                            logger.info("Gateway Request: method={}, url={}, headers={}, body={}", method, path, headers, bodyString);

                            Flux<DataBuffer> cachedFlux = Flux.defer(() -> Mono.just(new DefaultDataBufferFactory().wrap(bytes)));

                            ServerHttpRequest decorated = new ServerHttpRequestDecorator(request) {
                                @Override
                                public Flux<DataBuffer> getBody() {
                                    return cachedFlux;
                                }
                            };

                            return chain.filter(exchange.mutate().request(decorated).build())
                                    .doFinally(signal -> logger.info("Gateway Response for {} {}: {}", method, path, exchange.getResponse().getStatusCode()));
                        }
                    } finally {
                        DataBufferUtils.release(joined);
                    }
                });
    }
}