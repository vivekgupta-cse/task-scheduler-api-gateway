plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.taskscheduler"
version = "1.0-SNAPSHOT"
description = "TaskScheduler API Gateway"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencyManagement {
    imports {
        // Use the 2025.x release train for Spring Boot 4 compatibility
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.0")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // REPLACEMENT: In Boot 4.x, use the specific webflux gateway starter
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

    // Explicitly include WebFlux for the Netty runtime
    implementation("org.springframework.boot:spring-boot-starter-webflux")
}

