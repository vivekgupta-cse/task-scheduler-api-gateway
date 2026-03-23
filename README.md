and to inspect the dependency tree for conflicts:
# TaskScheduler API Gateway

A production-oriented Spring Cloud Gateway (Spring WebFlux) implementation that acts as a centralized API gateway in front of the TaskScheduler microservices. The gateway provides routing, request/response filters, CORS, security entry points, observability hooks, and a small set of operational features intended for local development and as a basis for production deployment.

This repository intentionally focuses on the gateway responsibilities only — downstream services (auth, tasks, etc.) own business logic and authentication. The gateway forwards requests to them and optionally performs cross-cutting concerns such as JWT validation, header enrichment, rate-limiting, request logging, and path normalization.

Table of contents
- Overview & architecture
- Quickstart (development)
- Configuration and routes
- Security model
- Filters and header propagation (RequestId, Logging, Rate limiting)
- Observability (logs, metrics, traces)
- Deployment & production checklist
- Testing and troubleshooting
- Contributing

Overview & architecture
-----------------------

High level:

- Clients -> API Gateway (this repo) -> Backend services (auth service on 8085, tasks service on 8086, ...)

Responsibilities of the gateway:

- Centralized routing and path handling (Spring Cloud Gateway)
- Lightweight request enrichment (X-Request-Id propagation, correlation)
- Cross-cutting features: CORS, authentication / authorization (optional), rate limiting, request logging
- Observability: structured logs and hooks for metrics/tracing

ASCII diagram:

    +--------+      +-------------+      +----------------+
    | Client | ---> | API Gateway | ---> | tasks service  |
    +--------+      +-------------+      +----------------+
                             |
                             +--> auth service (8085)

Quickstart (development)
-------------------------

Prerequisites
- Java JDK (project uses a Gradle toolchain, verify local JDK matches the requested toolchain or configure Gradle to use an installed JDK).
- Gradle wrapper included (use `./gradlew`).
- Downstream services should be running locally for end-to-end verification (auth service on `http://localhost:8085`, tasks service on `http://localhost:8086`).

Build and run

1. Build the project (skip tests for a quick local dev build):

```bash
./gradlew clean build -x test
```

2. Run the gateway in development mode:

```bash
./gradlew bootRun
```

3. Alternatively build a runnable JAR and start it:

```bash
./gradlew bootJar
java -jar build/libs/task-scheduler-api-gateway-*.jar
```

By default the gateway server port is configured in `src/main/resources/application.yaml` (commonly `8081`). The gateway routes to the downstream services configured in the same YAML.

Example requests to validate routing (assuming downstream services are running):

```bash
# Direct to tasks service (should succeed when called directly)
curl -v http://localhost:8086/api/tasks

# Through gateway (should be proxied to downstream tasks service)
curl -v http://localhost:8081/api/tasks
```

Configuration and routes
------------------------

All runtime configuration lives in `src/main/resources/application.yaml`. Key sections:

- `server.port` — gateway port (default 8081)
- `spring.cloud.gateway.routes` — route definitions and target URIs
- `spring.cloud.gateway.globalcors` — global CORS configuration

Example route snippet (existing):

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: task-scheduler-auth-service
              uri: http://localhost:8085
              predicates:
                - Path=/api/auth/**
            - id: task-scheduler-task-service
              uri: http://localhost:8086
              predicates:
                - Path=/api/tasks/**
```

Notes
- Route-level filters are powerful: you can rewrite paths, add or remove headers, set timeouts, or add circuit-breakers.
- For production you will usually externalize these settings (config server, environment variables, Kubernetes config maps, etc.).

Security model
--------------

This gateway can be used in two common modes:

1) Pass-through mode (default in this repo): gateway forwards requests (including Authorization header) to downstream services which perform authentication and authorization.
   - Use this when backend services are responsible for auth, or when you prefer a lightweight gateway.

2) Gateway-auth mode: the gateway validates tokens (e.g., JWT) and enforces auth policies centrally. The gateway then forwards an authenticated principal or asserts headers to downstream services.

Which to use depends on your operational model. The repo includes `SecurityConfig` where permitted paths and authentication settings are defined. For production-grade centralized auth you should wire a reactive JWT decoder and validate tokens at the gateway.

Filters and header propagation
------------------------------

This project includes several filters that implement common cross-cutting concerns — critically, `RequestIdFilter` and `LoggingFilter`.

- `RequestIdFilter` (reactive `WebFilter`):
  - Ensures every request has an `X-Request-Id`. If a client provides one it is preserved, otherwise a UUID is generated.
  - The filter sets the header on the proxied request (mutates the `ServerHttpRequest`) so downstream services receive the same `X-Request-Id` and also sets it on response headers.
  - The filter populates a `requestId` entry in the MDC so logs produced during the request processing include the correlation id.

- `LoggingFilter` (global filter):
  - Aggregates and logs request metadata and textual bodies up to a configurable size.
  - Logs response status codes and timing.

- `RateLimitFilter` (WebFilter):
  - Demonstrates simple IP-based rate limiting using Bucket4j and an in-memory Caffeine cache. For production, replace with a distributed rate limiter (Redis, etc.) to scale horizontally.

Important: header casing is case-insensitive. The gateway ensures the header `X-Request-Id` is added to the proxied request so downstream services can log and return it.

Observability (logs, metrics, traces)
-----------------------------------

Logging
- Filters write structured log lines with request path, method, headers and body snippet. The project includes Logback configuration in `src/main/resources/logback-spring.xml`.
- Ensure logs include the `requestId` MDC value.

Metrics & Tracing
- Integrate Micrometer for metrics and export to Prometheus. Add OTLP/OpenTelemetry configuration for distributed tracing.
- Suggested starters: `micrometer-registry-prometheus`, `opentelemetry-exporter-otlp`.

Production deployment & checklist
--------------------------------

Essentials
- Use TLS for client <-> gateway and gateway <-> backend communications.
- Use a discovery mechanism for backend targets (Eureka/Consul/Kubernetes service names) rather than hard-coded `localhost` URIs.
- Externalize configuration (secrets, JWT signing keys, rate limit configs) into a secure location — secret manager, vault, K8s secrets, etc.
- Use a distributed rate limiter and shared state (Redis, etc.) when running multiple gateway instances.
- Add readiness/liveness probes and health checks for the gateway and its downstream dependencies.

Security
- Validate and sanitize inbound headers. Carefully audit which headers are forwarded to backend services.
- If gateway terminates authentication, do not blindly forward authentication-sensitive headers unless intended.

Scaling
- Run multiple gateway instances behind a load balancer. Use sticky sessions only if required; prefer stateless JWTs for scale.

Operational concerns
- Centralized logging (ELK/EFK) and structured JSON logs.
- Centralized tracing (OpenTelemetry) to correlate requests across gateway and backends using `X-Request-Id`/trace ids.

Testing and troubleshooting
-------------------------

Local troubleshooting steps
- Check gateway logs (console or configured log file) for messages from `LoggingFilter` — these show Gateway Request/Response entries.
- Ensure downstream services are reachable: `curl -v http://localhost:8086/api/tasks`.
- When the upstream request fails with `401` issued by the gateway (WWW-Authenticate: Basic), verify `SecurityConfig` and permitted paths. The gateway may be rejecting the request before forwarding.

Verifying `X-Request-Id` propagation
1. Make a request without the header to the gateway:

```bash
curl -i -v 'http://localhost:8081/api/tasks' -H 'Content-Type: application/json' --data ''
```

2. The response should include an `X-Request-Id` response header. Check your downstream logs (task service) to see the same header value received.

3. Make a request with an explicit `X-Request-Id` and verify the same value reaches the backend:

```bash
curl -i -v 'http://localhost:8081/api/tasks' -H 'X-Request-Id: my-test-id' -H 'Content-Type: application/json' --data ''
```

If the downstream service does not receive the header:
- Confirm the gateway process was restarted after code changes.
- Confirm no other filters later in the chain are removing or overwriting the header.
- Ensure the downstream is not behind an additional proxy that strips headers.

Automated tests
- Unit tests in `src/test/java` cover filter behavior. Run:

```bash
./gradlew test
```

CI/CD and container image
-------------------------

Example Dockerfile (multi-stage) — keep images small and run as a non-root user in production. See `Dockerfile` example in the original repository for a pattern.

Add the gateway image to your CI pipeline and perform rolling updates with health checks. Use canary or blue/green deployments for safe rollouts.

Contributing
------------

- Open issues for bugs or feature requests.
- Open PRs against `main` with clear descriptions and tests for behavioral changes.
- Keep the gateway small and focused — prefer delegating business logic to downstream services.

License
-------

This repository does not include a license file. Add a `LICENSE` to specify terms if you intend to publish this publicly.

Contact / support
-----------------

If you want help extending the gateway (JWT integration, distributed rate limiting, tracing, or production hardening), open an issue or ask for a patch — I can provide implementation examples and CI-ready tests.

Author
------

Vivek Gupta

- Email: gvivek206@gmail.com

License
-------

This project is licensed under the Apache License

    Apache License
    Version 2.0, January 2004
    http://www.apache.org/licenses/

The full license text should be present in the `LICENSE` file at the repository root. The SPDX identifier for this license is `Apache-2.0`. When you reuse code from this repository, you must comply with the terms of the Apache License 2.0.

