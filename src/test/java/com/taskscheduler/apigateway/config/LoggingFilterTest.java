package com.taskscheduler.apigateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class LoggingFilterTest {

    private LoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoggingFilter();
    }

    @Test
    void filter_logsBodyAndProceedsWhenJson() {
        String json = "{\"foo\":\"bar\"}";
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(json.getBytes());

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/test")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Flux.just(buffer));

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        // invoke filter and ensure chain completes
        StepVerifier.create(filter.filter(exchange, (ex) -> {
            return ex.getResponse().setComplete();
        })).verifyComplete();
    }

    @Test
    void filter_skipsBodyWhenNotJson() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, (ex) -> ex.getResponse().setComplete()))
                .verifyComplete();
    }
}

