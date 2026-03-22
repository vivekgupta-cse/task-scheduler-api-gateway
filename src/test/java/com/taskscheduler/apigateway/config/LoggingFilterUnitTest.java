package com.taskscheduler.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingFilterUnitTest {

    @Test
    void nonBodyRequest_proceedsWithoutError() {
        LoggingFilter filter = new LoggingFilter();

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/hello")
                .header(HttpHeaders.ACCEPT, "*/*")
                .build();

        ServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, ex -> ex.getResponse().setComplete());

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void getOrder_returnsHighestPrecedence() {
        LoggingFilter filter = new LoggingFilter();
        // ensure ordering constant
        assertThat(filter.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void postWithoutContentType_treatedAsTextAndPassedDownstream() {
        LoggingFilter filter = new LoggingFilter();

        String json = "{\"hello\":\"world\"}";
        DataBuffer buf = new DefaultDataBufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/no-ct")
                .body(Flux.just(buf));

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, ex -> ex.getRequest().getBody()
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .doOnNext(list -> assertThat(list).containsExactly(json))
                .then());

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void smallJsonBody_isLoggedAndPassedDownstream() {
        LoggingFilter filter = new LoggingFilter();

        String json = "{\"foo\":\"bar\"}";
        DataBuffer buf = new DefaultDataBufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/test")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Flux.just(buf));

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // downstream chain that reads the body and asserts content
        Mono<Void> result = filter.filter(exchange, ex -> ex.getRequest().getBody()
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .doOnNext(list -> assertThat(list).containsExactly(json))
                .then());

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void largeBody_isTruncatedAndDownstreamReceivesEmptyBody() {
        LoggingFilter filter = new LoggingFilter();

        // create payload larger than MAX_LOG_BYTES (64KB)
        int big = 70 * 1024; // 70KB
        byte[] payload = new byte[big];
        for (int i = 0; i < payload.length; i++) payload[i] = 'a';

        DataBuffer buf = new DefaultDataBufferFactory().wrap(payload);

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/large")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(Flux.just(buf));

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, ex -> ex.getRequest().getBody()
                .map(db -> {
                    byte[] b = new byte[db.readableByteCount()];
                    db.read(b);
                    return b.length;
                })
                .collectList()
                .doOnNext(list -> {
                    // when oversized, our filter returns an empty body to downstream in this implementation
                    assertThat(list).containsExactly(0);
                })
                .then());

        StepVerifier.create(result).verifyComplete();
    }
}


