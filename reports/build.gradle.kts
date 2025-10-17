plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("io.spring.dependency-management")
}

dependencies {
    // Common and Security modules
    implementation(project(":common"))
    implementation(project(":security"))

    // Cross-module dependencies (reports need access to all domains)
    implementation(project(":students"))
    implementation(project(":courses"))
    implementation(project(":payments"))
    implementation(project(":exams"))

    // Spring Boot dependencies
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Redis for caching
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // PostgreSQL
    runtimeOnly("org.postgresql:postgresql")

    // Apache POI for Excel reports
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // iText for PDF reports
    implementation("com.itextpdf:itext7-core:7.2.5")

    // MapStruct for DTO mapping
    implementation("org.mapstruct:mapstruct:1.5.5.Final")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.5.5.Final")
}
