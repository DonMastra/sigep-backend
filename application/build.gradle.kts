plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    // All bounded context modules
    implementation(project(":common"))
    implementation(project(":security"))
    implementation(project(":students"))
    implementation(project(":courses"))
    implementation(project(":staff"))
    implementation(project(":scheduling"))
    implementation(project(":payments"))
    implementation(project(":tuition"))
    implementation(project(":exams"))
    implementation(project(":communications"))
    implementation(project(":reports"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Swagger/OpenAPI for API documentation - Updated to 2.7.0 for Spring Boot 3.5.x compatibility
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")

    // Monitoring and metrics
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Development tools
    developmentOnly("org.springframework.boot:spring-boot-devtools")
}

tasks.bootJar {
    enabled = true
    archiveFileName.set("sigep-backend.jar")
}

tasks.jar {
    enabled = false
}
