# TaskScheduler API Gateway

A small Spring Boot API Gateway based on Spring Cloud Gateway (WebFlux) that proxies requests to an existing backend (by default `http://localhost:9090`).

This repository contains a minimal gateway intended for local development and as a starting point for production-ready API gateway features (routing, CORS, filters, resilience, observability).

---

## What's included

- `src/main/java/com/taskscheduler/apigateway/ApiGateway.java` — Spring Boot main application.
- `src/main/resources/application.yaml` — gateway configuration (routes, CORS, server port).
- `build.gradle.kts` — Kotlin Gradle build with Spring Boot and Spring Cloud dependency management.

---

## Quick overview

- The gateway is configured to forward all incoming requests (catch-all route) to `http://localhost:9090` by default.
- The project uses the reactive stack (Spring WebFlux + Spring Cloud Gateway).

---

## Prerequisites

- Java JDK installed. This project sets a Java toolchain in `build.gradle.kts` (Java language version: 25). Ensure a matching JDK is installed on your machine. If you prefer a different JDK, update the toolchain or run Gradle with a compatible Java.
- Gradle wrapper included — use the wrapper (`./gradlew`) to build and run so you get the expected Gradle version.

---

## Build & run (development)

1. Build the project (skip tests locally):

```bash
./gradlew clean build -x test
```

2. Run the gateway from Gradle:

```bash
./gradlew bootRun
```

3. Alternatively build the fat JAR and run it:

```bash
./gradlew bootJar
java -jar build/libs/task-scheduler-api-gateway-1.0-SNAPSHOT.jar
```

By default the gateway reads `src/main/resources/application.yaml` for configuration. The configured server port in the repository is `8081` — requests to `http://localhost:8081` are forwarded to the backend at `http://localhost:9090`.

Example request (should be proxied to your backend):

```bash
curl -v http://localhost:8081/any/path
```

If your backend requires a path prefix, the gateway will forward the full path. Adjust routing or add filters (RewritePath) if you need to strip or rewrite prefixes.

---

## Configuration (important files)

- `src/main/resources/application.yaml` — central configuration used by Spring Boot. Key snippets:

  - server port:

    ```yaml
    server:
      port: 8081
    ```

  - catch-all route (forwards everything to the backend):

    ```yaml
    spring:
      cloud:
        gateway:
          routes:
            - id: task-manager-monolith
              uri: http://localhost:9090
              predicates:
                - Path=/**
    ```

  - global CORS (development permissive example):

    ```yaml
    spring:
      cloud:
        gateway:
          globalcors:
            corsConfigurations:
              '[/**]':
                allowedOrigins: "*"
                allowedMethods: [GET, POST, PUT, DELETE, OPTIONS]
    ```

Notes:
- To change the backend target, edit the `uri` in the route above.
- To restrict CORS in production, replace `"*"` with a list of allowed origins.
- You can override any property at runtime via environment variables (for example `SPRING_APPLICATION_JSON`) or the standard Spring Boot property precedence.

---

## Common changes you will want to make

- Add route filters (rewrite path, add/remove headers, rate limiting): see Spring Cloud Gateway `filters` in `application.yaml`.
- Enable Actuator & monitoring: add `spring-boot-starter-actuator` and configure management endpoints. In production, protect actuator endpoints.
- Add security (OAuth2/JWT) to the gateway or integrate with a centralized auth service.
- Add resilience (Circuit Breaker, retry) and rate limiting (Redis-based limits or token buckets).

---

## Troubleshooting

If you encounter startup exceptions similar to these:

- `Failed to generate bean name for imported class 'org.springframework.cloud.autoconfigure.LifecycleMvcEndpointAutoConfiguration'`
- `Failed to generate bean name for imported class 'org.springframework.cloud.autoconfigure.RefreshAutoConfiguration'`

These are usually caused by an auto-configuration class referencing servlet/MVC classes or Spring Cloud modules that are not on the classpath for a purely reactive application. Ways to fix:

1. Ensure dependencies are aligned with your Spring Boot version. Use the Spring Cloud BOM that matches your Spring Boot release (dependency management). The project uses Spring Boot 4.x — be sure to pick the matching Spring Cloud release train.

2. Exclude the problematic auto-configurations if you don't need them. Two approaches:

- Application property (in `application.yaml`):
  ```yaml
  spring:
    autoconfigure:
      exclude:
        - org.springframework.cloud.autoconfigure.LifecycleMvcEndpointAutoConfiguration
        - org.springframework.cloud.autoconfigure.RefreshAutoConfiguration
  ```

- Or in the main class via annotation:
  ```java
  @SpringBootApplication(exclude = {
      org.springframework.cloud.autoconfigure.LifecycleMvcEndpointAutoConfiguration.class,
      org.springframework.cloud.autoconfigure.RefreshAutoConfiguration.class
  })
  public class ApiGateway { ... }
  ```

3. If the app still fails, run with stacktrace and info logging and paste the output here. Useful commands:

```bash
./gradlew bootRun --stacktrace --info
``` 

and to inspect the dependency tree for conflicts:

```bash
./gradlew dependencies --configuration runtimeClasspath
```

---

## Docker (example)

A simple Dockerfile for packaging and running the built JAR (multi-stage build):

```dockerfile
# Build stage
FROM gradle:9-jdk17 as build
WORKDIR /src
COPY . /src
RUN gradle bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

Adjust the base JDK in the Dockerfile to match the toolchain you use locally.

---

## Production considerations & checklist

- Align Spring Boot / Spring Cloud versions using the BOM. Keep dependency management consistent across modules.
- Configure TLS (HTTPS) for the gateway and secure backend connections.
- Add authentication, authorization, and centralized identity (opaque tokens / JWT) if the gateway terminates auth.
- Limit CORS to trusted origins and enable `allowCredentials` only if needed.
- Add request/response logging and tracing (OpenTelemetry) and hook up to centralized observability.
- Harden headers (HSTS, X-Content-Type-Options, X-Frame-Options, etc.).
- Add rate limiting, quotas, and circuit breakers.
- Add health checks and readiness/liveness endpoints (Actuator) behind proper protections.

---

## Contributing

- Create issues for bugs or feature requests.
- Open pull requests against `main` with descriptive titles and link to issues when relevant.

---

## License

This project does not include a license file. Add a `LICENSE` file to specify terms if you intend to share it publicly.

---

If you'd like, I can:
- Add a `Dockerfile` and `Makefile` to streamline local development.
- Add example `curl` tests and a small integration test that starts the gateway and verifies a proxied request (requires a running backend or a stub).
- Align the Gradle build to a specific Spring Cloud release train if you tell me which Spring Boot version you prefer.

Tell me which additional items you'd like me to add to the README or repo (Dockerfile, example tests, stricter CORS policies, etc.) and I will implement them.
