plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}


tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // Generate JaCoCo report after tests run
}

// Configure JaCoCo reporting
jacoco {
    toolVersion = "0.8.13"
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
        // Use a Spring Cloud BOM compatible with Spring Boot 4.x
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Cloud Gateway (new webflux module for Spring Boot 4.x)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

    // Reactive runtime
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.6.0")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}