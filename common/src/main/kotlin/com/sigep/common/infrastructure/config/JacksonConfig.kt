package com.sigep.common.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Configuración de Jackson para soporte de tipos Java 8 date/time y Kotlin data classes.
 *
 * Registra explícitamente:
 * - KotlinModule: permite deserializar data classes de Kotlin (sin constructor vacío)
 * - JavaTimeModule: soporta LocalDate, LocalDateTime, Instant, ZonedDateTime, etc.
 */
@Configuration
class JacksonConfig {

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        val mapper = ObjectMapper()

        // Registrar módulo Kotlin para soportar data classes (sin @JsonCreator ni constructor vacío)
        mapper.registerModule(KotlinModule.Builder().build())

        // Registrar módulo para tipos Java 8 date/time
        mapper.registerModule(JavaTimeModule())

        // Configurar para NO usar timestamps (usar formato ISO-8601, ej: "2007-04-12")
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

        return mapper
    }
}

