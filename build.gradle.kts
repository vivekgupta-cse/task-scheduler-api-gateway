group = "com.taskscheduler"
version = "1.0-SNAPSHOT"
description = "TaskScheduler API Gateway"

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.4"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // Generate JaCoCo report after tests run
}

// Configure JaCoCo reporting
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test) // tests are required to run before generating the report
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

dependencyManagement {
    imports {
        // Use a Spring Cloud BOM compatible with Spring Boot 4.x
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.1")
    }
}

dependencies {
    // Spring Cloud Gateway (new webflux module for Spring Boot 4.x)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")

    // Reactive runtime
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    implementation("com.bucket4j:bucket4j-core:8.10.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.6.0")
}

// Add manifest information for jars (author, license) so packaged artifacts carry metadata
val projectAuthor = "Vivek Gupta"
val projectAuthorEmail = "gvivek206@gmail.com"
val projectLicenseName = "Apache License, Version 2.0"
val projectLicenseUrl = "https://www.apache.org/licenses/LICENSE-2.0.txt"
val projectUrl = "https://github.com/vivekgupta-cse/task-scheduler-api-gateway"


// Add manifest attributes (author/license metadata) to both plain JAR and bootJar artifacts
tasks.withType<Jar> {
    manifest {
        attributes(mapOf(
            "Implementation-Title" to project.description,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to projectAuthor,
            "Implementation-Vendor-Email" to projectAuthorEmail,
            "Implementation-URL" to projectUrl,
            "Built-By" to projectAuthor,
            "Implementation-License" to projectLicenseName,
            "Implementation-License-URL" to projectLicenseUrl
        ))
    }
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    manifest {
        attributes(mapOf(
            "Implementation-Title" to project.description,
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to projectAuthor,
            "Implementation-Vendor-Email" to projectAuthorEmail,
            "Implementation-URL" to projectUrl,
            "Built-By" to projectAuthor,
            "Implementation-License" to projectLicenseName,
            "Implementation-License-URL" to projectLicenseUrl
        ))
    }
}

