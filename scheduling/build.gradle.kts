plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencies {
    // Only depends on common (interfaces) and security (JWT/roles)
    // Cross-module data flows via interfaces declared in common (ReservationInfoProvider)
    implementation(project(":common"))
    implementation(project(":security"))

    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Redis for caching
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Swagger/OpenAPI for API documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
}
