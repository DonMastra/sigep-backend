plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencies {
    // Common module
    implementation(project(":common"))

    // Spring Security & JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // Redis for token management
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Rate limiting
    implementation("com.bucket4j:bucket4j-core:8.10.1")

    // Swagger/OpenAPI for API documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")
}
