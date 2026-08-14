plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencies {
    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.security:spring-security-core")

    // Jackson support for Java 8 date/time types (LocalDate, LocalDateTime, etc.)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // PostgreSQL (runtime only)
    runtimeOnly("org.postgresql:postgresql")
}
